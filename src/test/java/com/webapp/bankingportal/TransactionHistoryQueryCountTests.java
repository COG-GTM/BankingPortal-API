package com.webapp.bankingportal;

import java.util.Arrays;
import java.util.HashMap;

import org.hamcrest.Matchers;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

import lombok.val;

public class TransactionHistoryQueryCountTests extends BaseTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager entityManager;

    private Statistics statistics;

    @BeforeEach
    void enableStatistics() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    private HashMap<String, String> createUserWithTransfers(double... amounts) throws Exception {
        val userDetails = createAndLoginUser();
        val accountNumber = userDetails.get("accountNumber");
        val pin = getRandomPin();
        accountService.createPin(accountNumber, userDetails.get("password"), pin);

        var total = 0.0;
        for (val amount : amounts) {
            total += amount;
        }
        accountService.cashDeposit(accountNumber, pin, total);

        for (val amount : amounts) {
            val target = createAccount().get("accountNumber");
            accountService.fundTransfer(accountNumber, target, pin, amount);
        }

        entityManager.flush();
        entityManager.clear();
        return userDetails;
    }

    private long countHistoryQueries(int counterparties) throws Exception {
        val amounts = new double[counterparties];
        Arrays.fill(amounts, 100.0);
        val userDetails = createUserWithTransfers(amounts);
        statistics.clear();

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/account/transactions?size=10")
                .header("Authorization", "Bearer " + userDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isOk());

        val count = statistics.getPrepareStatementCount();
        System.out.printf("QUERY_COUNT counterparties=%d transactions=%d statements=%d%n",
                counterparties, counterparties + 1, count);
        return count;
    }

    @Test
    public void test_transaction_history_query_count_is_independent_of_history_size() throws Exception {
        val small = countHistoryQueries(15);
        val large = countHistoryQueries(30);
        Assertions.assertEquals(small, large);
    }

    @Test
    public void test_transaction_history_is_paged_with_metadata_headers() throws Exception {
        val userDetails = createUserWithTransfers(100, 200, 300, 400, 500);

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/account/transactions?page=1&size=2")
                .header("Authorization", "Bearer " + userDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().string("X-Total-Count", "6"))
                .andExpect(MockMvcResultMatchers.header().string("X-Total-Pages", "3"))
                .andExpect(MockMvcResultMatchers.header().string("X-Page", "1"))
                .andExpect(MockMvcResultMatchers.header().string("X-Page-Size", "2"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2));
    }

    @Test
    public void test_transaction_history_default_sort_is_newest_first() throws Exception {
        val userDetails = createUserWithTransfers(100, 200, 300);

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/account/transactions")
                .header("Authorization", "Bearer " + userDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(4))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].amount").value(300.0))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].transactionType").value("CASH_TRANSFER"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[3].transactionType").value("CASH_DEPOSIT"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[3].targetAccountNumber").value("N/A"));
    }

    @Test
    public void test_transaction_history_sorted_by_amount_ascending() throws Exception {
        val userDetails = createUserWithTransfers(300, 100, 200);

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/account/transactions?sort=amount,asc&size=3")
                .header("Authorization", "Bearer " + userDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[*].amount",
                        Matchers.contains(100.0, 200.0, 300.0)));
    }

    @Test
    public void test_transaction_history_page_size_is_capped() throws Exception {
        val userDetails = createUserWithTransfers(100);

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/account/transactions?size=1000")
                .header("Authorization", "Bearer " + userDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().string("X-Page-Size", "100"));
    }

    @Test
    public void test_transaction_history_rejects_unsupported_sort_property() throws Exception {
        val userDetails = createUserWithTransfers(100);

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/account/transactions?sort=sourceAccount.user.password,asc")
                .header("Authorization", "Bearer " + userDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

}
