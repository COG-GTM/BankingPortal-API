package com.webapp.bankingportal.exception;

public class TransactionAlreadyReversedException extends RuntimeException {

    public TransactionAlreadyReversedException(String message) {
        super(message);
    }

}
