package com.webapp.bankingportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.webapp.bankingportal.dto.AmountRequest;
import com.webapp.bankingportal.dto.FundTransferRequest;
import com.webapp.bankingportal.exception.IdempotencyKeyInProgressException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.service.IdempotencyService;
import com.webapp.bankingportal.type.CacheKeyType;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;

import lombok.val;

public class IdempotencyKeyTests extends BaseTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private ResultActions post(String path, String token, String idempotencyKey, Object body) throws Exception {
        val request = MockMvcRequestBuilders.post(path)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(body));
        if (idempotencyKey != null) {
            request.header(IdempotencyService.IDEMPOTENCY_KEY_HEADER, idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    private double balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).getBalance();
    }

    @Test
    public void test_repeat_withdrawal_without_key_runs_again() throws Exception {
        val user = createAndLoginUserWithInitialBalance(1000.0);
        val token = "Bearer " + user.get("token");
        val request = new AmountRequest(user.get("accountNumber"), user.get("pin"), 200.0);

        post("/api/account/withdraw", token, null, request).andExpect(MockMvcResultMatchers.status().isOk());
        post("/api/account/withdraw", token, null, request).andExpect(MockMvcResultMatchers.status().isOk());

        assertEquals(600.0, balanceOf(user.get("accountNumber")));
    }

    @Test
    public void test_repeat_deposit_with_new_key_runs_again() throws Exception {
        val user = createAndLoginUserWithInitialBalance(1000.0);
        val token = "Bearer " + user.get("token");
        val request = new AmountRequest(user.get("accountNumber"), user.get("pin"), 100.0);

        post("/api/account/deposit", token, UUID.randomUUID().toString(), request)
                .andExpect(MockMvcResultMatchers.status().isOk());
        post("/api/account/deposit", token, UUID.randomUUID().toString(), request)
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertEquals(1200.0, balanceOf(user.get("accountNumber")));
    }

    @Test
    public void test_retried_transfer_with_same_key_moves_money_once() throws Exception {
        val user = createAndLoginUserWithInitialBalance(1000.0);
        val token = "Bearer " + user.get("token");
        val target = createAccount().get("accountNumber");
        val request = new FundTransferRequest(user.get("accountNumber"), target, 300.0, user.get("pin"));
        val key = UUID.randomUUID().toString();

        post("/api/account/fund-transfer", token, key, request)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().doesNotExist(IdempotencyService.REPLAYED_HEADER));
        post("/api/account/fund-transfer", token, key, request)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().string(IdempotencyService.REPLAYED_HEADER, "true"))
                .andExpect(MockMvcResultMatchers.content().string(ApiMessages.CASH_TRANSFER_SUCCESS.getMessage()));

        assertEquals(700.0, balanceOf(user.get("accountNumber")));
        assertEquals(300.0, balanceOf(target));
    }

    @Test
    public void test_same_key_with_different_request_is_rejected() throws Exception {
        val user = createAndLoginUserWithInitialBalance(1000.0);
        val token = "Bearer " + user.get("token");
        val key = UUID.randomUUID().toString();

        post("/api/account/withdraw", token, key, new AmountRequest(user.get("accountNumber"), user.get("pin"), 100.0))
                .andExpect(MockMvcResultMatchers.status().isOk());
        post("/api/account/withdraw", token, key, new AmountRequest(user.get("accountNumber"), user.get("pin"), 200.0))
                .andExpect(MockMvcResultMatchers.status().isUnprocessableEntity())
                .andExpect(MockMvcResultMatchers.content().string(ApiMessages.IDEMPOTENCY_KEY_REUSED.getMessage()));

        assertEquals(900.0, balanceOf(user.get("accountNumber")));
    }

    @Test
    public void test_failed_request_frees_its_key_for_retry() throws Exception {
        val user = createAndLoginUserWithInitialBalance(1000.0);
        val token = "Bearer " + user.get("token");
        val request = new AmountRequest(user.get("accountNumber"), user.get("pin"), 2000.0);
        val key = UUID.randomUUID().toString();

        post("/api/account/withdraw", token, key, request)
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
        post("/api/account/deposit", token, null, new AmountRequest(user.get("accountNumber"), user.get("pin"), 1000.0))
                .andExpect(MockMvcResultMatchers.status().isOk());
        post("/api/account/withdraw", token, key, request)
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertEquals(0.0, balanceOf(user.get("accountNumber")));
    }

    @Test
    public void test_invalid_key_is_rejected() throws Exception {
        val user = createAndLoginUserWithInitialBalance(1000.0);
        val token = "Bearer " + user.get("token");

        post("/api/account/withdraw", token, "x".repeat(256),
                new AmountRequest(user.get("accountNumber"), user.get("pin"), 100.0))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().string(ApiMessages.IDEMPOTENCY_KEY_INVALID.getMessage()));

        assertEquals(1000.0, balanceOf(user.get("accountNumber")));
    }

    @Test
    public void test_simultaneous_pair_with_same_key_runs_once() throws Exception {
        val accountNumber = getRandomAccountNumber();
        val key = UUID.randomUUID().toString();
        val request = new AmountRequest(accountNumber, "1234", 100.0);
        val executions = new AtomicInteger();
        val conflicts = new AtomicInteger();
        val barrier = new CyclicBarrier(2);
        val executor = Executors.newFixedThreadPool(2);

        Runnable call = () -> {
            try {
                barrier.await();
                idempotencyService.execute(accountNumber, "/api/account/withdraw", key, request, () -> {
                    executions.incrementAndGet();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return ResponseEntity.ok(ApiMessages.CASH_WITHDRAWAL_SUCCESS.getMessage());
                });
            } catch (IdempotencyKeyInProgressException e) {
                conflicts.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        val first = executor.submit(call);
        val second = executor.submit(call);
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, executions.get());
        assertEquals(1, conflicts.get());

        val replay = idempotencyService.execute(accountNumber, "/api/account/withdraw", key, request, () -> {
            executions.incrementAndGet();
            return ResponseEntity.ok(ApiMessages.CASH_WITHDRAWAL_SUCCESS.getMessage());
        });
        assertEquals("true", replay.getHeaders().getFirst(IdempotencyService.REPLAYED_HEADER));
        assertEquals(1, executions.get());
    }

    @Test
    public void test_pin_is_left_out_of_the_stored_fingerprint() throws Exception {
        val user = createAndLoginUserWithInitialBalance(1000.0);
        val token = "Bearer " + user.get("token");
        val accountNumber = user.get("accountNumber");
        val request = new AmountRequest(accountNumber, user.get("pin"), 100.0);
        val key = UUID.randomUUID().toString();

        post("/api/account/deposit", token, key, request).andExpect(MockMvcResultMatchers.status().isOk());

        val stored = (String) redisTemplate.opsForValue()
                .get(CacheKeyType.IDEMPOTENCY.generateKey(accountNumber, "/api/account/deposit", key));
        val hashWithPin = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(JsonUtil.objectMapper.writeValueAsBytes(request)));
        assertFalse(stored.contains(hashWithPin));
        assertFalse(stored.contains(user.get("pin")));

        post("/api/account/deposit", token, key, new AmountRequest(accountNumber, "0000", 100.0))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().string(IdempotencyService.REPLAYED_HEADER, "true"));
        assertEquals(1100.0, balanceOf(accountNumber));
    }

    @Test
    public void test_in_progress_marker_lasts_as_long_as_the_stored_response() {
        val accountNumber = getRandomAccountNumber();
        val key = UUID.randomUUID().toString();
        val redisKey = CacheKeyType.IDEMPOTENCY.generateKey(accountNumber, "/api/account/withdraw", key);
        val minimumTtl = CacheKeyType.IDEMPOTENCY.getTtlSeconds() - 60;
        val executions = new AtomicInteger();

        idempotencyService.execute(accountNumber, "/api/account/withdraw", key,
                new AmountRequest(accountNumber, "1234", 100.0), () -> {
                    executions.incrementAndGet();
                    assertTrue(redisTemplate.getExpire(redisKey, TimeUnit.SECONDS) > minimumTtl);
                    return ResponseEntity.ok(ApiMessages.CASH_WITHDRAWAL_SUCCESS.getMessage());
                });

        assertEquals(1, executions.get());
        assertTrue(redisTemplate.getExpire(redisKey, TimeUnit.SECONDS) > minimumTtl);
    }
}
