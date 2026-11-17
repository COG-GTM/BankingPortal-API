package com.webapp.bankingportal.service;

import java.util.function.Supplier;

import org.springframework.http.ResponseEntity;

public interface IdempotencyService {

    String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    String REPLAYED_HEADER = "Idempotent-Replayed";

    /**
     * Runs {@code action} at most once per account, endpoint and client-supplied
     * idempotency key. A retry with the same key and request gets the stored
     * response back without running the action again. Without a key the action
     * always runs.
     */
    ResponseEntity<String> execute(String accountNumber, String endpoint, String idempotencyKey,
            Object request, Supplier<ResponseEntity<String>> action);
}
