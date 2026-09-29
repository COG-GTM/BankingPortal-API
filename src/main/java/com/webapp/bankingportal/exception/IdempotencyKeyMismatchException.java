package com.webapp.bankingportal.exception;

public class IdempotencyKeyMismatchException extends RuntimeException {

    public IdempotencyKeyMismatchException(String message) {
        super(message);
    }

}
