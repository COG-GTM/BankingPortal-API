package com.webapp.bankingportal.exception;

public class ExchangeRateUnavailableException extends RuntimeException {

    public ExchangeRateUnavailableException(String message) {
        super(message);
    }
}
