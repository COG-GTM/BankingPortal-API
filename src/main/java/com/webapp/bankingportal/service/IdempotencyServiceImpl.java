package com.webapp.bankingportal.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.webapp.bankingportal.exception.IdempotencyKeyInProgressException;
import com.webapp.bankingportal.exception.IdempotencyKeyReusedException;
import com.webapp.bankingportal.type.CacheKeyType;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {

    static final long IN_PROGRESS_TTL_SECONDS = 300;
    private static final Pattern VALID_KEY = Pattern.compile("[\\x21-\\x7E]{1,255}");

    private final CacheService cacheService;

    private record StoredRequest(String fingerprint, boolean completed, int status, String body) {
    }

    @Override
    public ResponseEntity<String> execute(String accountNumber, String endpoint, String idempotencyKey,
            Object request, Supplier<ResponseEntity<String>> action) {
        if (idempotencyKey == null) {
            return action.get();
        }
        if (!VALID_KEY.matcher(idempotencyKey).matches()) {
            throw new IllegalArgumentException(ApiMessages.IDEMPOTENCY_KEY_INVALID.getMessage());
        }

        val fingerprint = fingerprint(request);
        val inProgress = toJson(new StoredRequest(fingerprint, false, 0, null));
        if (!cacheService.putIfAbsent(CacheKeyType.IDEMPOTENCY, inProgress, IN_PROGRESS_TTL_SECONDS,
                accountNumber, endpoint, idempotencyKey)) {
            return replay(accountNumber, endpoint, idempotencyKey, fingerprint);
        }

        ResponseEntity<String> response;
        try {
            response = action.get();
        } catch (RuntimeException e) {
            cacheService.delete(CacheKeyType.IDEMPOTENCY, accountNumber, endpoint, idempotencyKey);
            throw e;
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            cacheService.delete(CacheKeyType.IDEMPOTENCY, accountNumber, endpoint, idempotencyKey);
            return response;
        }

        try {
            cacheService.put(CacheKeyType.IDEMPOTENCY,
                    toJson(new StoredRequest(fingerprint, true, response.getStatusCode().value(), response.getBody())),
                    accountNumber, endpoint, idempotencyKey);
        } catch (RuntimeException e) {
            log.error("Could not store idempotent response for key {}: {}", idempotencyKey, e.getMessage());
        }
        return response;
    }

    private ResponseEntity<String> replay(String accountNumber, String endpoint, String idempotencyKey,
            String fingerprint) {
        val stored = cacheService.get(CacheKeyType.IDEMPOTENCY, String.class, accountNumber, endpoint, idempotencyKey)
                .map(this::fromJson)
                .orElseThrow(() -> new IdempotencyKeyInProgressException(
                        ApiMessages.IDEMPOTENCY_KEY_IN_PROGRESS.getMessage()));

        if (!stored.fingerprint().equals(fingerprint)) {
            throw new IdempotencyKeyReusedException(ApiMessages.IDEMPOTENCY_KEY_REUSED.getMessage());
        }
        if (!stored.completed()) {
            throw new IdempotencyKeyInProgressException(ApiMessages.IDEMPOTENCY_KEY_IN_PROGRESS.getMessage());
        }

        return ResponseEntity.status(HttpStatus.valueOf(stored.status()))
                .header(REPLAYED_HEADER, "true")
                .body(stored.body());
    }

    private static String fingerprint(Object request) {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
                    .digest(JsonUtil.objectMapper.writeValueAsBytes(request));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String toJson(StoredRequest storedRequest) {
        try {
            return JsonUtil.objectMapper.writeValueAsString(storedRequest);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private StoredRequest fromJson(String json) {
        try {
            return JsonUtil.objectMapper.readValue(json, StoredRequest.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
