package com.webapp.bankingportal;

import java.util.List;
import java.util.Map;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.webapp.bankingportal.dto.FundTransferRequest;
import com.webapp.bankingportal.dto.TransferReversalRequest;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;

import lombok.val;

public class TransferReversalControllerTests extends BaseTest {

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransactionRepository transactionRepository;

    private Long transfer(Map<String, String> sender, String targetAccountNumber, double amount) throws Exception {
        val fundTransferRequest = new FundTransferRequest(sender.get("accountNumber"), targetAccountNumber, amount,
                sender.get("pin"));

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/account/fund-transfer")
                .header("Authorization", "Bearer " + sender.get("token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(fundTransferRequest)))
                .andExpect(MockMvcResultMatchers.status().isOk());

        val senderAccountNumber = sender.get("accountNumber");
        return transactionRepository
                .findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(senderAccountNumber,
                        senderAccountNumber)
                .stream()
                .filter(t -> t.getTransactionType() == TransactionType.CASH_TRANSFER)
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private MockHttpServletRequestBuilder reverse(Object transactionId, String token, String pin) {
        var builder = MockMvcRequestBuilders
                .post("/api/account/fund-transfer/{transactionId}/reverse", transactionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new TransferReversalRequest(pin)));
        if (token != null) {
            builder = builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private double balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).getBalance();
    }

    @Test
    public void test_reverse_fund_transfer_with_valid_data() throws Exception {
        val sender = createAndLoginUserWithInitialBalance(1000);
        val recipient = createAndLoginUser();
        val transactionId = transfer(sender, recipient.get("accountNumber"), 400);

        mockMvc.perform(reverse(transactionId, sender.get("token"), sender.get("pin")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.transactionType")
                        .value(TransactionType.CASH_TRANSFER_REVERSAL.name()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.amount").value(400.0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.sourceAccountNumber")
                        .value(recipient.get("accountNumber")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.targetAccountNumber")
                        .value(sender.get("accountNumber")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.reversedTransactionId").value(transactionId));

        Assertions.assertEquals(1000, balanceOf(sender.get("accountNumber")));
        Assertions.assertEquals(0, balanceOf(recipient.get("accountNumber")));
    }

    @Test
    public void test_reverse_fund_transfer_twice() throws Exception {
        val sender = createAndLoginUserWithInitialBalance(1000);
        val recipient = createAndLoginUser();
        val transactionId = transfer(sender, recipient.get("accountNumber"), 400);

        mockMvc.perform(reverse(transactionId, sender.get("token"), sender.get("pin")))
                .andExpect(MockMvcResultMatchers.status().isOk());

        mockMvc.perform(reverse(transactionId, sender.get("token"), sender.get("pin")))
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.TRANSACTION_ALREADY_REVERSED.getMessage()));

        Assertions.assertEquals(1000, balanceOf(sender.get("accountNumber")));
        Assertions.assertEquals(0, balanceOf(recipient.get("accountNumber")));
    }

    @Test
    public void test_reverse_fund_transfer_without_authorization() throws Exception {
        val sender = createAndLoginUserWithInitialBalance(1000);
        val recipient = createAndLoginUser();
        val transactionId = transfer(sender, recipient.get("accountNumber"), 400);

        mockMvc.perform(reverse(transactionId, null, sender.get("pin")))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());

        Assertions.assertEquals(600, balanceOf(sender.get("accountNumber")));
    }

    @Test
    public void test_reverse_fund_transfer_by_recipient() throws Exception {
        val sender = createAndLoginUserWithInitialBalance(1000);
        val recipient = createAndLoginUserWithPin();
        val transactionId = transfer(sender, recipient.get("accountNumber"), 400);

        mockMvc.perform(reverse(transactionId, recipient.get("token"), recipient.get("pin")))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.TRANSACTION_REVERSAL_FORBIDDEN.getMessage()));

        Assertions.assertEquals(600, balanceOf(sender.get("accountNumber")));
        Assertions.assertEquals(400, balanceOf(recipient.get("accountNumber")));
    }

    @Test
    public void test_reverse_fund_transfer_by_unrelated_user() throws Exception {
        val sender = createAndLoginUserWithInitialBalance(1000);
        val recipient = createAndLoginUser();
        val outsider = createAndLoginUserWithPin();
        val transactionId = transfer(sender, recipient.get("accountNumber"), 400);

        mockMvc.perform(reverse(transactionId, outsider.get("token"), outsider.get("pin")))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.TRANSACTION_NOT_FOUND.getMessage()));

        Assertions.assertEquals(600, balanceOf(sender.get("accountNumber")));
        Assertions.assertEquals(400, balanceOf(recipient.get("accountNumber")));
    }

    @Test
    public void test_reverse_fund_transfer_with_invalid_pin() throws Exception {
        val sender = createAndLoginUserWithInitialBalance(1000);
        val recipient = createAndLoginUser();
        val transactionId = transfer(sender, recipient.get("accountNumber"), 400);

        var wrongPin = getRandomPin();
        while (wrongPin.equals(sender.get("pin"))) {
            wrongPin = getRandomPin();
        }

        mockMvc.perform(reverse(transactionId, sender.get("token"), wrongPin))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.PIN_INVALID_ERROR.getMessage()));

        Assertions.assertEquals(600, balanceOf(sender.get("accountNumber")));
    }

    @Test
    public void test_reverse_fund_transfer_with_missing_body() throws Exception {
        val sender = createAndLoginUserWithInitialBalance(1000);
        val recipient = createAndLoginUser();
        val transactionId = transfer(sender, recipient.get("accountNumber"), 400);

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/account/fund-transfer/{transactionId}/reverse", transactionId)
                .header("Authorization", "Bearer " + sender.get("token")))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.PIN_EMPTY_ERROR.getMessage()));

        Assertions.assertEquals(600, balanceOf(sender.get("accountNumber")));
    }

    @Test
    public void test_reverse_nonexistent_transaction() throws Exception {
        val sender = createAndLoginUserWithInitialBalance(1000);

        mockMvc.perform(reverse(Long.MAX_VALUE, sender.get("token"), sender.get("pin")))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.TRANSACTION_NOT_FOUND.getMessage()));
    }

    @Test
    public void test_reverse_transaction_with_invalid_id() throws Exception {
        val sender = createAndLoginUserWithInitialBalance(1000);

        mockMvc.perform(reverse("not-a-number", sender.get("token"), sender.get("pin")))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content()
                        .string(String.format(ApiMessages.PARAMETER_INVALID_ERROR.getMessage(), "transactionId")));
    }

    @Test
    public void test_reverse_deposit_transaction() throws Exception {
        val sender = createAndLoginUserWithInitialBalance(1000);
        val senderAccountNumber = sender.get("accountNumber");
        val depositId = transactionRepository
                .findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(senderAccountNumber,
                        senderAccountNumber)
                .stream()
                .filter(t -> t.getTransactionType() == TransactionType.CASH_DEPOSIT)
                .findFirst()
                .orElseThrow()
                .getId();

        mockMvc.perform(reverse(depositId, sender.get("token"), sender.get("pin")))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.TRANSACTION_NOT_REVERSIBLE.getMessage()));

        Assertions.assertEquals(1000, balanceOf(senderAccountNumber));
    }

    @Test
    public void test_reversal_appears_in_transaction_history() throws Exception {
        val sender = createAndLoginUserWithInitialBalance(1000);
        val recipient = createAndLoginUser();
        val transactionId = transfer(sender, recipient.get("accountNumber"), 400);

        mockMvc.perform(reverse(transactionId, sender.get("token"), sender.get("pin")))
                .andExpect(MockMvcResultMatchers.status().isOk());

        for (val user : List.of(sender, recipient)) {
            mockMvc.perform(MockMvcRequestBuilders
                    .get("/api/account/transactions")
                    .header("Authorization", "Bearer " + user.get("token")))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.content().string(CoreMatchers.containsString(
                            "\"transactionType\":\"" + TransactionType.CASH_TRANSFER_REVERSAL.name() + "\"")))
                    .andExpect(MockMvcResultMatchers.content().string(CoreMatchers.containsString(
                            "\"reversedTransactionId\":" + transactionId)));
        }
    }

}
