package com.webapp.bankingportal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.webapp.bankingportal.dto.FundTransferRequest;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.service.IdempotencyService;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;

import lombok.val;

/**
 * Runs without a test-managed transaction so each request commits (or rolls back) on its own,
 * exercising the real retry and concurrency behaviour of the persisted idempotency key.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class FundTransferIdempotencyConcurrencyTests extends BaseTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private MvcResult transfer(String token, String idempotencyKey, FundTransferRequest request) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders
                .post("/api/account/fund-transfer")
                .header("Authorization", "Bearer " + token)
                .header(IdempotencyService.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(request)))
                .andReturn();
    }

    private long transferCount(String accountNumber) {
        return transactionRepository
                .findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(accountNumber, accountNumber)
                .stream()
                .filter(t -> t.getTransactionType() == TransactionType.CASH_TRANSFER)
                .count();
    }

    @Test
    public void test_concurrent_retries_with_same_key_post_exactly_once() throws Exception {
        val source = createAndLoginUserWithBalanceViaService(1000);
        val target = createAccount().get("accountNumber");
        val request = new FundTransferRequest(source.get("accountNumber"), target, 300, source.get("pin"));
        val key = UUID.randomUUID().toString();
        val attempts = 5;

        val executor = Executors.newFixedThreadPool(attempts);
        val start = new CountDownLatch(1);
        val tasks = new ArrayList<Callable<MvcResult>>();
        for (int i = 0; i < attempts; i++) {
            tasks.add(() -> {
                start.await();
                return transfer(source.get("token"), key, request);
            });
        }

        try {
            val futures = tasks.stream().map(executor::submit).toList();
            start.countDown();

            int executed = 0;
            for (val future : futures) {
                val response = future.get(30, TimeUnit.SECONDS).getResponse();
                assertEquals(200, response.getStatus(), response.getContentAsString());
                assertEquals(ApiMessages.CASH_TRANSFER_SUCCESS.getMessage(), response.getContentAsString());
                if (response.getHeader(IdempotencyService.IDEMPOTENT_REPLAYED_HEADER) == null) {
                    executed++;
                }
            }
            assertEquals(1, executed);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(700, accountRepository.findByAccountNumber(source.get("accountNumber")).getBalance());
        assertEquals(300, accountRepository.findByAccountNumber(target).getBalance());
        assertEquals(1, transferCount(source.get("accountNumber")));
    }

    @Test
    public void test_failed_transfer_does_not_consume_key_and_can_be_retried() throws Exception {
        val source = createAndLoginUserWithBalanceViaService(100);
        val target = createAccount().get("accountNumber");
        val request = new FundTransferRequest(source.get("accountNumber"), target, 500, source.get("pin"));
        val key = UUID.randomUUID().toString();

        val failed = transfer(source.get("token"), key, request).getResponse();
        assertEquals(400, failed.getStatus());
        assertEquals(ApiMessages.BALANCE_INSUFFICIENT_ERROR.getMessage(), failed.getContentAsString());

        accountService.cashDeposit(source.get("accountNumber"), source.get("pin"), 900);

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/account/fund-transfer")
                .header("Authorization", "Bearer " + source.get("token"))
                .header(IdempotencyService.IDEMPOTENCY_KEY_HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(request)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().doesNotExist(IdempotencyService.IDEMPOTENT_REPLAYED_HEADER));

        val replay = transfer(source.get("token"), key, request).getResponse();
        assertEquals(200, replay.getStatus());
        assertEquals("true", replay.getHeader(IdempotencyService.IDEMPOTENT_REPLAYED_HEADER));

        assertEquals(500, accountRepository.findByAccountNumber(source.get("accountNumber")).getBalance());
        assertEquals(1, transferCount(source.get("accountNumber")));
    }
}
