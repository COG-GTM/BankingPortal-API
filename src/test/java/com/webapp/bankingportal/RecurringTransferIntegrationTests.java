package com.webapp.bankingportal;

import java.time.LocalDate;
import java.util.HashMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.jayway.jsonpath.JsonPath;
import com.webapp.bankingportal.dto.RecurringTransferRequest;
import com.webapp.bankingportal.entity.RecurringTransferStatus;
import com.webapp.bankingportal.entity.TransferFrequency;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.RecurringTransferRepository;
import com.webapp.bankingportal.service.RecurringTransferService;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.val;

public class RecurringTransferIntegrationTests extends BaseTest {

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    RecurringTransferRepository recurringTransferRepository;

    @Autowired
    RecurringTransferService recurringTransferService;

    private static String requestJson(String target, double amount, String pin, String frequency, LocalDate start,
            LocalDate end) {
        return """
                {"targetAccountNumber": "%s", "amount": %s, "pin": "%s", "frequency": "%s",
                 "startDate": "%s", "endDate": "%s"}
                """.formatted(target, amount, pin, frequency, start, end);
    }

    /**
     * Sets up PIN and balance through the service layer rather than the
     * idempotency-cached /pin/create and /deposit endpoints.
     */
    private HashMap<String, String> loggedInUserWithBalance(double balance) throws Exception {
        val userDetails = createAndLoginUser();
        val pin = getRandomPin();
        accountService.createPin(userDetails.get("accountNumber"), userDetails.get("password"), pin);
        accountService.cashDeposit(userDetails.get("accountNumber"), pin, balance);
        userDetails.put("pin", pin);
        return userDetails;
    }

    @Test
    public void test_create_list_and_cancel_recurring_transfer_via_api() throws Exception {
        val source = loggedInUserWithBalance(1000);
        val target = createAccount();
        val today = LocalDate.now();

        val createResult = mockMvc.perform(MockMvcRequestBuilders
                .post("/api/account/recurring-transfers")
                .header("Authorization", "Bearer " + source.get("token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(target.get("accountNumber"), 200, source.get("pin"), "MONTHLY",
                        today.plusDays(1), today.plusMonths(6))))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("ACTIVE"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.frequency").value("MONTHLY"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.nextExecutionDate").value(today.plusDays(1).toString()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.targetAccountNumber").value(target.get("accountNumber")))
                .andReturn();
        Integer id = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/account/recurring-transfers")
                .header("Authorization", "Bearer " + source.get("token")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].id").value(id));

        mockMvc.perform(MockMvcRequestBuilders
                .delete("/api/account/recurring-transfers/" + id)
                .header("Authorization", "Bearer " + source.get("token")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.RECURRING_TRANSFER_CANCEL_SUCCESS.getMessage()));

        Assertions.assertEquals(RecurringTransferStatus.CANCELLED,
                recurringTransferRepository.findById(id.longValue()).orElseThrow().getStatus());
    }

    @Test
    public void test_create_recurring_transfer_with_invalid_pin_via_api() throws Exception {
        val source = loggedInUserWithBalance(1000);
        val target = createAccount();
        val today = LocalDate.now();

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/account/recurring-transfers")
                .header("Authorization", "Bearer " + source.get("token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(target.get("accountNumber"), 200, "0000".equals(source.get("pin")) ? "1111" : "0000",
                        "WEEKLY", today, today.plusWeeks(2))))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());

        Assertions.assertTrue(recurringTransferRepository
                .findBySourceAccount_AccountNumberOrderByIdAsc(source.get("accountNumber")).isEmpty());
    }

    @Test
    public void test_create_recurring_transfer_with_end_before_start_via_api() throws Exception {
        val source = loggedInUserWithBalance(1000);
        val target = createAccount();
        val today = LocalDate.now();

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/account/recurring-transfers")
                .header("Authorization", "Bearer " + source.get("token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(target.get("accountNumber"), 200, source.get("pin"), "WEEKLY",
                        today.plusDays(7), today)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.RECURRING_TRANSFER_END_BEFORE_START.getMessage()));
    }

    @Test
    public void test_execute_weekly_schedule_moves_funds_until_end_date() {
        val source = createAccountWithInitialBalance(1000);
        val target = createAccountWithInitialBalance(100);
        val today = LocalDate.now();

        val transfer = recurringTransferService.createRecurringTransfer(source.get("accountNumber"),
                new RecurringTransferRequest(target.get("accountNumber"), 300, source.get("pin"),
                        TransferFrequency.WEEKLY, today, today.plusWeeks(1)));

        recurringTransferService.executeDueTransfers(today);

        Assertions.assertEquals(700, accountRepository.findByAccountNumber(source.get("accountNumber")).getBalance(), 0.01);
        Assertions.assertEquals(400, accountRepository.findByAccountNumber(target.get("accountNumber")).getBalance(), 0.01);
        var saved = recurringTransferRepository.findById(transfer.getId()).orElseThrow();
        Assertions.assertEquals(today.plusWeeks(1), saved.getNextExecutionDate());
        Assertions.assertEquals(RecurringTransferStatus.ACTIVE, saved.getStatus());

        // Not due again until next week.
        Assertions.assertEquals(0, recurringTransferService.executeDueTransfers(today.plusDays(3)));

        recurringTransferService.executeDueTransfers(today.plusWeeks(1));

        Assertions.assertEquals(400, accountRepository.findByAccountNumber(source.get("accountNumber")).getBalance(), 0.01);
        Assertions.assertEquals(700, accountRepository.findByAccountNumber(target.get("accountNumber")).getBalance(), 0.01);
        saved = recurringTransferRepository.findById(transfer.getId()).orElseThrow();
        Assertions.assertEquals(RecurringTransferStatus.COMPLETED, saved.getStatus());
        Assertions.assertNull(saved.getNextExecutionDate());
        Assertions.assertEquals(2, saved.getOccurrencesProcessed());

        Assertions.assertEquals(0, recurringTransferService.executeDueTransfers(today.plusWeeks(2)));
    }

    @Test
    public void test_execute_schedule_with_insufficient_balance_records_failure() {
        val source = createAccountWithInitialBalance(100);
        val target = createAccount();
        val today = LocalDate.now();

        val transfer = recurringTransferService.createRecurringTransfer(source.get("accountNumber"),
                new RecurringTransferRequest(target.get("accountNumber"), 500, source.get("pin"),
                        TransferFrequency.MONTHLY, today, today.plusMonths(2)));

        recurringTransferService.executeDueTransfers(today);

        Assertions.assertEquals(100, accountRepository.findByAccountNumber(source.get("accountNumber")).getBalance(), 0.01);
        Assertions.assertEquals(0, accountRepository.findByAccountNumber(target.get("accountNumber")).getBalance(), 0.01);
        val saved = recurringTransferRepository.findById(transfer.getId()).orElseThrow();
        Assertions.assertEquals(1, saved.getFailedOccurrences());
        Assertions.assertEquals(ApiMessages.BALANCE_INSUFFICIENT_ERROR.getMessage(), saved.getLastFailureReason());
        Assertions.assertEquals(today.plusMonths(1), saved.getNextExecutionDate());
    }
}
