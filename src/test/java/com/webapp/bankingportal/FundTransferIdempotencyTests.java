package com.webapp.bankingportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.webapp.bankingportal.dto.FundTransferRequest;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.IdempotencyRecordRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.service.IdempotencyService;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;

import lombok.val;

public class FundTransferIdempotencyTests extends BaseTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private IdempotencyService idempotencyService;

    private ResultActions transfer(String token, String idempotencyKey, FundTransferRequest request) throws Exception {
        val builder = MockMvcRequestBuilders
                .post("/api/account/fund-transfer")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(request));
        if (idempotencyKey != null) {
            builder.header(IdempotencyService.IDEMPOTENCY_KEY_HEADER, idempotencyKey);
        }
        return mockMvc.perform(builder);
    }

    private double balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).getBalance();
    }

    private long transferCount(String accountNumber) {
        return transactionRepository
                .findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(accountNumber, accountNumber)
                .stream()
                .filter(t -> t.getTransactionType() == TransactionType.CASH_TRANSFER)
                .count();
    }

    @Test
    public void test_retry_with_same_key_replays_response_without_double_posting() throws Exception {
        val source = createAndLoginUserWithBalanceViaService(1000);
        val target = createAccount().get("accountNumber");
        val request = new FundTransferRequest(source.get("accountNumber"), target, 300, source.get("pin"));
        val key = UUID.randomUUID().toString();

        transfer(source.get("token"), key, request)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().doesNotExist(IdempotencyService.IDEMPOTENT_REPLAYED_HEADER))
                .andExpect(MockMvcResultMatchers.content().string(ApiMessages.CASH_TRANSFER_SUCCESS.getMessage()));

        transfer(source.get("token"), key, request)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().string(IdempotencyService.IDEMPOTENT_REPLAYED_HEADER, "true"))
                .andExpect(MockMvcResultMatchers.content().string(ApiMessages.CASH_TRANSFER_SUCCESS.getMessage()));

        assertEquals(700, balanceOf(source.get("accountNumber")));
        assertEquals(300, balanceOf(target));
        assertEquals(1, transferCount(source.get("accountNumber")));
    }

    @Test
    public void test_retry_with_same_key_and_different_payload_is_rejected() throws Exception {
        val source = createAndLoginUserWithBalanceViaService(1000);
        val target = createAccount().get("accountNumber");
        val key = UUID.randomUUID().toString();

        transfer(source.get("token"), key,
                new FundTransferRequest(source.get("accountNumber"), target, 300, source.get("pin")))
                .andExpect(MockMvcResultMatchers.status().isOk());

        transfer(source.get("token"), key,
                new FundTransferRequest(source.get("accountNumber"), target, 500, source.get("pin")))
                .andExpect(MockMvcResultMatchers.status().isUnprocessableEntity())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.IDEMPOTENCY_KEY_MISMATCH_ERROR.getMessage()));

        assertEquals(700, balanceOf(source.get("accountNumber")));
        assertEquals(1, transferCount(source.get("accountNumber")));
    }

    @Test
    public void test_different_keys_post_separate_transfers() throws Exception {
        val source = createAndLoginUserWithBalanceViaService(1000);
        val target = createAccount().get("accountNumber");
        val request = new FundTransferRequest(source.get("accountNumber"), target, 300, source.get("pin"));

        transfer(source.get("token"), UUID.randomUUID().toString(), request)
                .andExpect(MockMvcResultMatchers.status().isOk());
        transfer(source.get("token"), UUID.randomUUID().toString(), request)
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertEquals(400, balanceOf(source.get("accountNumber")));
        assertEquals(2, transferCount(source.get("accountNumber")));
    }

    @Test
    public void test_same_key_is_scoped_per_account() throws Exception {
        val first = createAndLoginUserWithBalanceViaService(1000);
        val second = createAndLoginUserWithBalanceViaService(1000);
        val target = createAccount().get("accountNumber");
        val key = UUID.randomUUID().toString();

        transfer(first.get("token"), key,
                new FundTransferRequest(first.get("accountNumber"), target, 300, first.get("pin")))
                .andExpect(MockMvcResultMatchers.status().isOk());
        transfer(second.get("token"), key,
                new FundTransferRequest(second.get("accountNumber"), target, 300, second.get("pin")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().doesNotExist(IdempotencyService.IDEMPOTENT_REPLAYED_HEADER));

        assertEquals(700, balanceOf(first.get("accountNumber")));
        assertEquals(700, balanceOf(second.get("accountNumber")));
        assertEquals(600, balanceOf(target));
    }

    @Test
    public void test_retry_after_key_expired_executes_again() throws Exception {
        val source = createAndLoginUserWithBalanceViaService(1000);
        val target = createAccount().get("accountNumber");
        val request = new FundTransferRequest(source.get("accountNumber"), target, 300, source.get("pin"));
        val key = UUID.randomUUID().toString();

        transfer(source.get("token"), key, request).andExpect(MockMvcResultMatchers.status().isOk());

        val record = idempotencyRecordRepository
                .findByAccountNumberAndIdempotencyKey(source.get("accountNumber"), key).orElseThrow();
        record.setExpiresAt(Instant.now().minusSeconds(1));
        idempotencyRecordRepository.saveAndFlush(record);

        transfer(source.get("token"), key, request)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().doesNotExist(IdempotencyService.IDEMPOTENT_REPLAYED_HEADER));

        assertEquals(400, balanceOf(source.get("accountNumber")));
        assertEquals(2, transferCount(source.get("accountNumber")));
    }

    @Test
    public void test_key_is_persisted_with_ttl_and_purged_after_expiry() throws Exception {
        val source = createAndLoginUserWithBalanceViaService(1000);
        val target = createAccount().get("accountNumber");
        val key = UUID.randomUUID().toString();

        transfer(source.get("token"), key,
                new FundTransferRequest(source.get("accountNumber"), target, 300, source.get("pin")))
                .andExpect(MockMvcResultMatchers.status().isOk());

        val record = idempotencyRecordRepository
                .findByAccountNumberAndIdempotencyKey(source.get("accountNumber"), key).orElseThrow();
        assertEquals(200, record.getResponseStatus());
        assertEquals(ApiMessages.CASH_TRANSFER_SUCCESS.getMessage(), record.getResponseBody());
        assertTrue(record.getExpiresAt().isAfter(record.getCreatedAt().plusSeconds(24 * 3600 - 1)));

        idempotencyService.purgeExpired();
        assertTrue(idempotencyRecordRepository
                .findByAccountNumberAndIdempotencyKey(source.get("accountNumber"), key).isPresent());

        record.setExpiresAt(Instant.now().minusSeconds(1));
        idempotencyRecordRepository.saveAndFlush(record);
        idempotencyService.purgeExpired();
        assertTrue(idempotencyRecordRepository
                .findByAccountNumberAndIdempotencyKey(source.get("accountNumber"), key).isEmpty());
    }

    @Test
    public void test_blank_or_oversized_key_is_rejected() throws Exception {
        val source = createAndLoginUserWithBalanceViaService(1000);
        val target = createAccount().get("accountNumber");
        val request = new FundTransferRequest(source.get("accountNumber"), target, 300, source.get("pin"));

        transfer(source.get("token"), " ", request)
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
        transfer(source.get("token"), "k".repeat(256), request)
                .andExpect(MockMvcResultMatchers.status().isBadRequest());

        assertEquals(1000, balanceOf(source.get("accountNumber")));
    }

    @Test
    public void test_request_without_key_is_not_deduplicated() throws Exception {
        val source = createAndLoginUserWithBalanceViaService(1000);
        val target = createAccount().get("accountNumber");
        val request = new FundTransferRequest(source.get("accountNumber"), target, 300, source.get("pin"));

        transfer(source.get("token"), null, request).andExpect(MockMvcResultMatchers.status().isOk());
        transfer(source.get("token"), null, request).andExpect(MockMvcResultMatchers.status().isOk());

        assertEquals(400, balanceOf(source.get("accountNumber")));
    }
}
