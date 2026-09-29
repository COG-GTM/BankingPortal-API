package com.webapp.bankingportal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.Transaction;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;

import lombok.val;

public class TransactionSearchTests extends BaseTest {

    private static final String SEARCH_URL = "/api/account/transactions/search";

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private String token;

    @BeforeEach
    public void seedTransactions() throws Exception {
        val userDetails = createAndLoginUser();
        token = userDetails.get("token");

        val account = accountRepository.findByAccountNumber(userDetails.get("accountNumber"));
        val otherAccount = accountRepository.findByAccountNumber(createAccount().get("accountNumber"));

        saveTransaction(account, null, TransactionType.CASH_DEPOSIT, 500, LocalDate.of(2024, 1, 5));
        saveTransaction(account, null, TransactionType.CASH_WITHDRAWAL, 200, LocalDate.of(2024, 1, 10));
        saveTransaction(account, otherAccount, TransactionType.CASH_TRANSFER, 1000, LocalDate.of(2024, 1, 15));
        saveTransaction(otherAccount, account, TransactionType.CASH_TRANSFER, 300, LocalDate.of(2024, 1, 20));
        saveTransaction(account, null, TransactionType.CASH_DEPOSIT, 50, LocalDate.of(2024, 1, 25));

        saveTransaction(otherAccount, null, TransactionType.CASH_DEPOSIT, 700, LocalDate.of(2024, 1, 12));
    }

    private void saveTransaction(Account source, Account target, TransactionType type, double amount,
            LocalDate date) {
        val transaction = new Transaction();
        transaction.setSourceAccount(source);
        transaction.setTargetAccount(target);
        transaction.setTransactionType(type);
        transaction.setAmount(amount);
        transaction.setTransactionDate(
                Date.from(date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant()));
        transactionRepository.save(transaction);
    }

    private ResultActions search(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private static MockHttpServletRequestBuilder searchRequest() {
        return MockMvcRequestBuilders.get(SEARCH_URL);
    }

    private static void expectAmounts(ResultActions result, Double... amounts) throws Exception {
        result.andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[*].amount", Matchers.contains(amounts)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(amounts.length));
    }

    @Test
    public void test_search_without_filters_returns_all_account_transactions_newest_first() throws Exception {
        search(searchRequest())
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[*].amount",
                        Matchers.contains(50.0, 300.0, 1000.0, 200.0, 500.0)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.page").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.size").value(20))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(5))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalPages").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.first").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.last").value(true));
    }

    @Test
    public void test_search_includes_incoming_transfers_and_excludes_other_accounts() throws Exception {
        search(searchRequest().param("type", "CASH_TRANSFER"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[*].amount",
                        Matchers.contains(300.0, 1000.0)));

        search(searchRequest().param("minAmount", "700").param("maxAmount", "700"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content", Matchers.empty()));
    }

    @Test
    public void test_search_by_date_range_is_inclusive() throws Exception {
        expectAmounts(search(searchRequest()
                .param("startDate", "2024-01-10")
                .param("endDate", "2024-01-20")), 300.0, 1000.0, 200.0);
    }

    @Test
    public void test_search_by_single_day() throws Exception {
        expectAmounts(search(searchRequest()
                .param("startDate", "2024-01-15")
                .param("endDate", "2024-01-15")), 1000.0);
    }

    @Test
    public void test_search_with_open_ended_date_range() throws Exception {
        expectAmounts(search(searchRequest().param("startDate", "2024-01-20")), 50.0, 300.0);
        expectAmounts(search(searchRequest().param("endDate", "2024-01-09")), 500.0);
    }

    @Test
    public void test_search_by_single_transaction_type() throws Exception {
        expectAmounts(search(searchRequest().param("type", "CASH_DEPOSIT")), 50.0, 500.0);
    }

    @Test
    public void test_search_by_multiple_transaction_types() throws Exception {
        expectAmounts(search(searchRequest().param("type", "CASH_DEPOSIT", "CASH_WITHDRAWAL")),
                50.0, 200.0, 500.0);
        expectAmounts(search(searchRequest().param("type", "CASH_WITHDRAWAL,CASH_TRANSFER")),
                300.0, 1000.0, 200.0);
    }

    @Test
    public void test_search_by_amount_range_is_inclusive() throws Exception {
        expectAmounts(search(searchRequest()
                .param("minAmount", "200")
                .param("maxAmount", "500")), 300.0, 200.0, 500.0);
    }

    @Test
    public void test_search_with_open_ended_amount_range() throws Exception {
        expectAmounts(search(searchRequest().param("minAmount", "500")), 1000.0, 500.0);
        expectAmounts(search(searchRequest().param("maxAmount", "200")), 50.0, 200.0);
    }

    @Test
    public void test_search_with_combined_filters() throws Exception {
        expectAmounts(search(searchRequest()
                .param("startDate", "2024-01-01")
                .param("endDate", "2024-01-31")
                .param("type", "CASH_TRANSFER", "CASH_DEPOSIT")
                .param("minAmount", "100")), 300.0, 1000.0, 500.0);
    }

    @Test
    public void test_search_pagination() throws Exception {
        search(searchRequest().param("page", "0").param("size", "2"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[*].amount", Matchers.contains(50.0, 300.0)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(5))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalPages").value(3))
                .andExpect(MockMvcResultMatchers.jsonPath("$.first").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.last").value(false));

        search(searchRequest().param("page", "1").param("size", "2"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[*].amount", Matchers.contains(1000.0, 200.0)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.page").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.first").value(false))
                .andExpect(MockMvcResultMatchers.jsonPath("$.last").value(false));

        search(searchRequest().param("page", "2").param("size", "2"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[*].amount", Matchers.contains(500.0)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.last").value(true));

        search(searchRequest().param("page", "5").param("size", "2"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content", Matchers.empty()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(5));
    }

    @Test
    public void test_search_pagination_applies_after_filters() throws Exception {
        search(searchRequest().param("type", "CASH_DEPOSIT", "CASH_TRANSFER").param("size", "3").param("page", "1"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[*].amount", Matchers.contains(500.0)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(4))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalPages").value(2));
    }

    @Test
    public void test_search_sorted_by_amount() throws Exception {
        search(searchRequest().param("sortBy", "amount").param("sortDir", "asc"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[*].amount",
                        Matchers.contains(50.0, 200.0, 300.0, 500.0, 1000.0)));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "startDate | 2024-01-20 | endDate   | 2024-01-10 | startDate cannot be after endDate",
            "minAmount | 500        | maxAmount | 100        | minAmount cannot be greater than maxAmount",
            "minAmount | -1         | page      | 0          | Amount filters cannot be negative",
            "maxAmount | -1         | page      | 0          | Amount filters cannot be negative",
            "page      | -1         | size      | 10         | page must be greater than or equal to 0",
            "size      | 0          | page      | 0          | size must be between 1 and 100",
            "size      | 101        | page      | 0          | size must be between 1 and 100",
            "sortBy    | id         | page      | 0          | sortBy must be one of: transactionDate, amount",
            "type      | FOO        | page      | 0          | Invalid value 'FOO' for parameter 'type'",
            "startDate | 01/10/2024 | page      | 0          | Invalid value '01/10/2024' for parameter 'startDate'",
            "minAmount | abc        | page      | 0          | Invalid value 'abc' for parameter 'minAmount'",
    })
    public void test_search_with_invalid_parameters(String param1, String value1, String param2, String value2,
            String expectedMessage) throws Exception {
        search(searchRequest().param(param1, value1).param(param2, value2))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().string(expectedMessage));
    }

    @Test
    public void test_search_with_invalid_sort_direction() throws Exception {
        search(searchRequest().param("sortDir", "sideways"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    public void test_search_unauthorized_access() throws Exception {
        mockMvc.perform(searchRequest())
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

}
