package com.webapp.bankingportal.exception;

public class IncorrectPinException extends UnauthorizedException {

    public IncorrectPinException(String message) {
        super(message);
    }
}
