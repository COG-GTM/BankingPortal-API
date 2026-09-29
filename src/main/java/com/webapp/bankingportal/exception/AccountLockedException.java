package com.webapp.bankingportal.exception;

import java.time.Duration;

import lombok.Getter;

@Getter
public class AccountLockedException extends RuntimeException {

    private final Duration retryAfter;

    public AccountLockedException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }
}
