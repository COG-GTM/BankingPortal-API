package com.webapp.bankingportal.exception;

public class IdempotencyKeyInProgressException extends RuntimeException {

    public IdempotencyKeyInProgressException(String message) {
        super(message);
    }

}
