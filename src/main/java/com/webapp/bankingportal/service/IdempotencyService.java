package com.webapp.bankingportal.service;

import java.util.function.Supplier;

import org.springframework.http.ResponseEntity;

public interface IdempotencyService {

    String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    String IDEMPOTENT_REPLAYED_HEADER = "Idempotent-Replayed";

    /**
     * Runs {@code action} at most once per (accountNumber, idempotencyKey) within the configured TTL.
     * The key is persisted in the same transaction as the action, so a failed action leaves no record
     * and may be retried with the same key. A retry with a matching request replays the stored response;
     * a retry with a different request is rejected. A null key runs the action without idempotency.
     */
    ResponseEntity<String> execute(String accountNumber, String idempotencyKey, String requestHash,
            Supplier<ResponseEntity<String>> action);

    String hashRequest(String... parts);

    int purgeExpired();
}
