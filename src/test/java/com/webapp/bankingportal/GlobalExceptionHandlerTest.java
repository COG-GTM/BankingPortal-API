package com.webapp.bankingportal;

import lombok.val;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.MediaType;

import com.webapp.bankingportal.dto.AmountRequest;
import com.webapp.bankingportal.dto.FundTransferRequest;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.service.AccountService;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class GlobalExceptionHandlerTest extends BaseTest {

    @MockBean
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void stubAccountCreation() {
        Mockito.when(accountService.createAccount(Mockito.any(User.class))).thenAnswer(invocation -> {
            val account = new Account();
            account.setAccountNumber(getRandomAccountNumber());
            account.setUser(invocation.getArgument(0));
            return accountRepository.save(account);
        });
    }

    @Test
    void whenRedisConnectionFails_shouldReturn503() throws Exception {
        val userDetails = createAndLoginUser();
        val token = userDetails.get("token");

        Mockito.when(accountService.isPinCreated(Mockito.anyString()))
               .thenThrow(new RedisConnectionFailureException("Redis down"));

        mockMvc.perform(get("/api/account/pin/check")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message")
                .value(ApiMessages.REDIS_CONNECTION_FAILURE.getMessage()));
    }

    @Test
    void whenDepositPinMissing_shouldReturnStructuredValidationError() throws Exception {
        val token = createAndLoginUser().get("token");
        val request = new AmountRequest(null, null, 100.0);

        mockMvc.perform(post("/api/account/deposit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(ApiMessages.VALIDATION_FAILED.getMessage()))
                .andExpect(jsonPath("$.path").value("/api/account/deposit"))
                .andExpect(jsonPath("$.errors", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("pin"))
                .andExpect(jsonPath("$.errors[0].message").value(ApiMessages.PIN_EMPTY_ERROR.getMessage()));

        Mockito.verify(accountService, Mockito.never())
                .cashDeposit(Mockito.any(), Mockito.any(), Mockito.anyDouble());
    }

    @Test
    void whenWithdrawPinFormatInvalid_shouldReturnValidationError() throws Exception {
        val token = createAndLoginUser().get("token");
        val request = new AmountRequest(null, "12a", 100.0);

        mockMvc.perform(post("/api/account/withdraw")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value("/api/account/withdraw"))
                .andExpect(jsonPath("$.errors", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("pin"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value(ApiMessages.PIN_FORMAT_INVALID_ERROR.getMessage()));

        Mockito.verify(accountService, Mockito.never())
                .cashWithdrawal(Mockito.any(), Mockito.any(), Mockito.anyDouble());
    }

    @Test
    void whenDepositAmountNotPositive_shouldReturnValidationError() throws Exception {
        val token = createAndLoginUser().get("token");
        val request = new AmountRequest(null, getRandomPin(), 0.0);

        mockMvc.perform(post("/api/account/deposit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value(ApiMessages.AMOUNT_NEGATIVE_ERROR.getMessage()));
    }

    @Test
    void whenDepositAmountExceedsLimit_shouldReturnValidationError() throws Exception {
        val token = createAndLoginUser().get("token");
        val request = new AmountRequest(null, getRandomPin(), 100_100.0);

        mockMvc.perform(post("/api/account/deposit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value(ApiMessages.AMOUNT_EXCEED_100_000_ERROR.getMessage()));
    }

    @Test
    void whenFundTransferHasMultipleInvalidFields_shouldReturnAllErrorsSortedByField() throws Exception {
        val token = createAndLoginUser().get("token");
        val request = new FundTransferRequest(null, " ", -100.0, null);

        mockMvc.perform(post("/api/account/fund-transfer")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(ApiMessages.VALIDATION_FAILED.getMessage()))
                .andExpect(jsonPath("$.path").value("/api/account/fund-transfer"))
                .andExpect(jsonPath("$.errors", Matchers.hasSize(3)))
                .andExpect(jsonPath("$.errors[*].field",
                        Matchers.contains("amount", "pin", "targetAccountNumber")))
                .andExpect(jsonPath("$.errors[0].message").value(ApiMessages.AMOUNT_NEGATIVE_ERROR.getMessage()))
                .andExpect(jsonPath("$.errors[1].message").value(ApiMessages.PIN_EMPTY_ERROR.getMessage()))
                .andExpect(jsonPath("$.errors[2].message")
                        .value(ApiMessages.TARGET_ACCOUNT_EMPTY_ERROR.getMessage()));

        Mockito.verify(accountService, Mockito.never())
                .fundTransfer(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyDouble());
    }

    @Test
    void whenRequestBodyMalformed_shouldReturnStructuredError() throws Exception {
        val token = createAndLoginUser().get("token");

        mockMvc.perform(post("/api/account/deposit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\": \"1234\", \"amount\": \"not-a-number\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(ApiMessages.REQUEST_BODY_INVALID_ERROR.getMessage()))
                .andExpect(jsonPath("$.path").value("/api/account/deposit"))
                .andExpect(jsonPath("$.errors", Matchers.empty()));
    }

    @Test
    void whenAmountRequestValid_shouldPassValidationAndCallService() throws Exception {
        val token = createAndLoginUser().get("token");
        val pin = getRandomPin();
        val request = new AmountRequest(null, pin, 100.0);

        mockMvc.perform(post("/api/account/deposit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(request)))
                .andExpect(status().isOk())
                .andExpect(content().string(ApiMessages.CASH_DEPOSIT_SUCCESS.getMessage()));

        Mockito.verify(accountService).cashDeposit(Mockito.any(), Mockito.eq(pin), Mockito.eq(100.0));
    }
}
