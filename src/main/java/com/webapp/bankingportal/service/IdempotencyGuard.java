package com.webapp.bankingportal.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;

import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.webapp.bankingportal.exception.IdempotencyKeyConflictException;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.LoggedinUser;

import lombok.val;

/**
 * Runs a state-changing request at most once per idempotency key within the
 * cache's retention window and replays the stored response for retries.
 *
 * <p>With an {@code Idempotency-Key} header the key is the header itself (scoped
 * to the account and endpoint), and the request fingerprint stored with the
 * response must match: reusing a key for a different request (other amount,
 * target or PIN) is rejected. Without the header the fingerprint is the key.
 * Concurrent requests with the same key wait for the first one to finish.
 */
@Component
public class IdempotencyGuard {

    private final Cache<Object, Object> cache;

    @SuppressWarnings("unchecked")
    public IdempotencyGuard(CacheManager cacheManager) {
        this.cache = (Cache<Object, Object>) cacheManager.getCache("idempotency").getNativeCache();
    }

    private record Entry(String fingerprint, ResponseEntity<String> response) {
    }

    public ResponseEntity<String> execute(String endpoint, String clientKey, Supplier<ResponseEntity<String>> action,
            Object... fields) {
        val fingerprint = digest(fields);
        val scope = LoggedinUser.getAccountNumber() + ':' + endpoint + ':';
        val key = clientKey != null ? scope + "key:" + digest(clientKey) : scope + "body:" + fingerprint;

        val entry = (Entry) cache.get(key, k -> new Entry(fingerprint, action.get()));
        if (!entry.fingerprint().equals(fingerprint)) {
            throw new IdempotencyKeyConflictException(ApiMessages.IDEMPOTENCY_KEY_CONFLICT.getMessage());
        }
        return entry.response();
    }

    private static String digest(Object... values) {
        try {
            val digest = MessageDigest.getInstance("SHA-256");
            for (val value : values) {
                if (value == null) {
                    digest.update((byte) 0);
                    continue;
                }
                val bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
                digest.update((byte) 1);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
