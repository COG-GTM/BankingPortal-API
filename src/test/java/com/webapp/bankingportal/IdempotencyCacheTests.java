package com.webapp.bankingportal;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.webapp.bankingportal.controller.AccountController;
import com.webapp.bankingportal.dto.AmountRequest;
import com.webapp.bankingportal.dto.FundTransferRequest;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;

import lombok.val;

public class IdempotencyCacheTests extends BaseTest {

    @Autowired
    private CacheManager cacheManager;

    @Test
    public void test_every_account_controller_cache_is_registered() {
        Arrays.stream(AccountController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Cacheable.class))
                .filter(cacheable -> cacheable != null)
                .flatMap(cacheable -> Arrays.stream(cacheable.value()))
                .distinct()
                .forEach(name -> assertNotNull(cacheManager.getCache(name),
                        "Cache '" + name + "' is not registered in CacheConfig"));
    }

    @Test
    public void test_deposit_withdraw_and_transfer_succeed() throws Exception {
        val userDetails = createAndLoginUserWithInitialBalance(1000.0);
        val token = "Bearer " + userDetails.get("token");

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/account/withdraw")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(
                        new AmountRequest(userDetails.get("accountNumber"), userDetails.get("pin"), 200.0))))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.CASH_WITHDRAWAL_SUCCESS.getMessage()));

        val targetAccountNumber = createAccount().get("accountNumber");

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/account/fund-transfer")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new FundTransferRequest(userDetails.get("accountNumber"),
                        targetAccountNumber, 300.0, userDetails.get("pin")))))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.CASH_TRANSFER_SUCCESS.getMessage()));
    }
}
