package com.webapp.bankingportal;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.webapp.bankingportal.dto.AmountRequest;
import com.webapp.bankingportal.dto.BulkFundTransferRequest;
import com.webapp.bankingportal.dto.BulkTransferItem;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;

import lombok.val;

public class BulkFundTransferControllerTests extends BaseTest {

    private static final String BULK_TRANSFER_URL = "/api/account/fund-transfer/bulk";

    @Autowired
    AccountRepository accountRepository;

    private ResultActions postBulkTransfer(String token, BulkFundTransferRequest request) throws Exception {
        val builder = MockMvcRequestBuilders
                .post(BULK_TRANSFER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(request));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(builder);
    }

    private double balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).getBalance();
    }

    @Test
    public void test_bulk_transfer_all_valid() throws Exception {
        val userDetails = createAndLoginUserWithInitialBalance(1000.0);
        val target1 = createAndLoginUser().get("accountNumber");
        val target2 = createAndLoginUser().get("accountNumber");

        val request = new BulkFundTransferRequest(userDetails.get("pin"),
                List.of(new BulkTransferItem(target1, 300.0), new BulkTransferItem(target2, 200.0)), false);

        postBulkTransfer(userDetails.get("token"), request)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("COMPLETED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.total").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.succeeded").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalAmountTransferred").value(500.0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.results[0].status").value("SUCCESS"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.results[0].transactionId").isNumber())
                .andExpect(MockMvcResultMatchers.jsonPath("$.results[1].targetAccountNumber").value(target2));

        Assertions.assertEquals(500.0, balanceOf(userDetails.get("accountNumber")), 0.01);
    }

    @Test
    public void test_bulk_transfer_partial_failure_returns_multi_status() throws Exception {
        val userDetails = createAndLoginUserWithInitialBalance(1000.0);
        val target = createAndLoginUser().get("accountNumber");

        val request = new BulkFundTransferRequest(userDetails.get("pin"),
                List.of(new BulkTransferItem(target, 200.0),
                        new BulkTransferItem(getRandomAccountNumber(), 100.0),
                        new BulkTransferItem(target, 5000.0)),
                false);

        postBulkTransfer(userDetails.get("token"), request)
                .andExpect(MockMvcResultMatchers.status().isMultiStatus())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("PARTIALLY_COMPLETED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.succeeded").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.failed").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.results[0].status").value("SUCCESS"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.results[1].status").value("FAILED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.results[1].message")
                        .value(ApiMessages.ACCOUNT_NOT_FOUND.getMessage()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.results[2].message")
                        .value(ApiMessages.BALANCE_INSUFFICIENT_ERROR.getMessage()));

        Assertions.assertEquals(800.0, balanceOf(userDetails.get("accountNumber")), 0.01);
        Assertions.assertEquals(200.0, balanceOf(target), 0.01);
    }

    @Test
    public void test_bulk_transfer_all_failed_returns_unprocessable_entity() throws Exception {
        val userDetails = createAndLoginUserWithInitialBalance(100.0);
        val target = createAndLoginUser().get("accountNumber");

        val request = new BulkFundTransferRequest(userDetails.get("pin"),
                List.of(new BulkTransferItem(target, 500.0)), false);

        postBulkTransfer(userDetails.get("token"), request)
                .andExpect(MockMvcResultMatchers.status().isUnprocessableEntity())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("FAILED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.results[0].status").value("FAILED"));
    }

    @Test
    public void test_atomic_bulk_transfer_with_failure_applies_nothing() throws Exception {
        val userDetails = createAndLoginUserWithInitialBalance(1000.0);
        val target = createAndLoginUser().get("accountNumber");

        val request = new BulkFundTransferRequest(userDetails.get("pin"),
                List.of(new BulkTransferItem(target, 200.0), new BulkTransferItem(getRandomAccountNumber(), 100.0)),
                true);

        postBulkTransfer(userDetails.get("token"), request)
                .andExpect(MockMvcResultMatchers.status().isUnprocessableEntity())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("FAILED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.atomic").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.results[0].status").value("SKIPPED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.results[1].status").value("FAILED"));

        Assertions.assertEquals(1000.0, balanceOf(userDetails.get("accountNumber")), 0.01);
        Assertions.assertEquals(0.0, balanceOf(target), 0.01);
    }

    @Test
    public void test_bulk_transfer_replay_is_idempotent() throws Exception {
        val userDetails = createAndLoginUserWithInitialBalance(1000.0);
        val target = createAndLoginUser().get("accountNumber");

        val request = new BulkFundTransferRequest(userDetails.get("pin"),
                List.of(new BulkTransferItem(target, 200.0), new BulkTransferItem(getRandomAccountNumber(), 100.0)),
                false);

        val first = postBulkTransfer(userDetails.get("token"), request)
                .andExpect(MockMvcResultMatchers.status().isMultiStatus())
                .andReturn().getResponse().getContentAsString();
        val second = postBulkTransfer(userDetails.get("token"), request)
                .andExpect(MockMvcResultMatchers.status().isMultiStatus())
                .andReturn().getResponse().getContentAsString();

        Assertions.assertEquals(first, second);
        Assertions.assertEquals(800.0, balanceOf(userDetails.get("accountNumber")), 0.01);
        Assertions.assertEquals(200.0, balanceOf(target), 0.01);
    }

    @Test
    public void test_bulk_transfer_failed_batch_can_be_retried() throws Exception {
        val userDetails = createAndLoginUserWithInitialBalance(100.0);
        val target = createAndLoginUser().get("accountNumber");

        val request = new BulkFundTransferRequest(userDetails.get("pin"),
                List.of(new BulkTransferItem(target, 500.0)), false);

        postBulkTransfer(userDetails.get("token"), request)
                .andExpect(MockMvcResultMatchers.status().isUnprocessableEntity());

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/account/deposit")
                .header("Authorization", "Bearer " + userDetails.get("token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new AmountRequest(userDetails.get("accountNumber"),
                        userDetails.get("pin"), 400.0))))
                .andExpect(MockMvcResultMatchers.status().isOk());

        postBulkTransfer(userDetails.get("token"), request)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("COMPLETED"));

        Assertions.assertEquals(500.0, balanceOf(target), 0.01);
    }

    @Test
    public void test_bulk_transfer_with_invalid_pin() throws Exception {
        val userDetails = createAndLoginUserWithInitialBalance(1000.0);
        val target = createAndLoginUser().get("accountNumber");

        val request = new BulkFundTransferRequest(getRandomPin() + "0",
                List.of(new BulkTransferItem(target, 100.0)), false);

        postBulkTransfer(userDetails.get("token"), request)
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.content().string(ApiMessages.PIN_INVALID_ERROR.getMessage()));

        Assertions.assertEquals(1000.0, balanceOf(userDetails.get("accountNumber")), 0.01);
    }

    @Test
    public void test_bulk_transfer_with_empty_batch() throws Exception {
        val userDetails = createAndLoginUserWithInitialBalance(1000.0);

        val request = new BulkFundTransferRequest(userDetails.get("pin"), List.of(), false);

        postBulkTransfer(userDetails.get("token"), request)
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.BULK_TRANSFER_EMPTY_ERROR.getMessage()));
    }

    @Test
    public void test_bulk_transfer_unauthorized_access() throws Exception {
        val request = new BulkFundTransferRequest(getRandomPin(),
                List.of(new BulkTransferItem(getRandomAccountNumber(), 100.0)), false);

        postBulkTransfer(null, request)
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

}
