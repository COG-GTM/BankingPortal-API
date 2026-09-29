package com.webapp.bankingportal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.webapp.bankingportal.entity.TransactionType;

import lombok.val;

public class BalanceAuditControllerTests extends BaseTest {

    private static final String AUDIT_TRAIL_URL = "/api/account/audit-trail";

    @Test
    public void test_audit_trail_returns_own_entries_newest_first() throws Exception {
        val userDetails = createAndLoginUser();
        val accountNumber = userDetails.get("accountNumber");
        val pin = getRandomPin();
        accountService.createPin(accountNumber, userDetails.get("password"), pin);
        accountService.cashDeposit(accountNumber, pin, 1000);

        val other = createAccountWithInitialBalance(500);
        accountService.fundTransfer(accountNumber, other.get("accountNumber"), pin, 300);

        mockMvc.perform(MockMvcRequestBuilders
                .get(AUDIT_TRAIL_URL)
                .header("Authorization", "Bearer " + userDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.page").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.size").value(20))
                .andExpect(MockMvcResultMatchers.jsonPath("$.entries[*].accountNumber",
                        Matchers.everyItem(Matchers.is(accountNumber))))
                .andExpect(MockMvcResultMatchers.jsonPath("$.entries[0].operation")
                        .value(TransactionType.CASH_TRANSFER.name()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.entries[0].balanceBefore").value(1000.0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.entries[0].balanceAfter").value(700.0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.entries[0].performedBy").value(accountNumber))
                .andExpect(MockMvcResultMatchers.jsonPath("$.entries[0].counterpartyAccountNumber")
                        .value(other.get("accountNumber")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.entries[0].createdAt").isString())
                .andExpect(MockMvcResultMatchers.jsonPath("$.entries[1].operation")
                        .value(TransactionType.CASH_DEPOSIT.name()));
    }

    @Test
    public void test_audit_trail_with_pagination_and_date_range() throws Exception {
        val userDetails = createAndLoginUser();
        val accountNumber = userDetails.get("accountNumber");
        val pin = getRandomPin();
        accountService.createPin(accountNumber, userDetails.get("password"), pin);
        accountService.cashDeposit(accountNumber, pin, 1000);
        accountService.cashDeposit(accountNumber, pin, 2000);

        mockMvc.perform(MockMvcRequestBuilders
                .get(AUDIT_TRAIL_URL)
                .param("page", "1")
                .param("size", "1")
                .header("Authorization", "Bearer " + userDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalPages").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.entries.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.entries[0].balanceAfter").value(1000.0));

        val from = Instant.now().plus(1, ChronoUnit.HOURS);
        mockMvc.perform(MockMvcRequestBuilders
                .get(AUDIT_TRAIL_URL)
                .param("from", from.toString())
                .header("Authorization", "Bearer " + userDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());

        mockMvc.perform(MockMvcRequestBuilders
                .get(AUDIT_TRAIL_URL)
                .param("from", from.toString())
                .param("to", from.plus(1, ChronoUnit.HOURS).toString())
                .header("Authorization", "Bearer " + userDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(0));
    }

    @Test
    public void test_audit_trail_with_invalid_parameters() throws Exception {
        val userDetails = createAndLoginUser();

        mockMvc.perform(MockMvcRequestBuilders
                .get(AUDIT_TRAIL_URL)
                .param("size", "1000")
                .header("Authorization", "Bearer " + userDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());

        mockMvc.perform(MockMvcRequestBuilders
                .get(AUDIT_TRAIL_URL)
                .param("from", "not-a-date")
                .header("Authorization", "Bearer " + userDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    public void test_audit_trail_without_authorization() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                .get(AUDIT_TRAIL_URL))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

}
