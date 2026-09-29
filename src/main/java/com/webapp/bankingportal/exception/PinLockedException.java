package com.webapp.bankingportal.exception;

public class PinLockedException extends RuntimeException {

    public PinLockedException(String message) {
        super(message);
    }
}
