package com.webapp.bankingportal.exception;

public class IdempotencyKeyInvalidException extends RuntimeException {

    public IdempotencyKeyInvalidException(String message) {
        super(message);
    }

}
