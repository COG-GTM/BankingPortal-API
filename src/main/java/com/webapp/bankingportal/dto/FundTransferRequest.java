package com.webapp.bankingportal.dto;

public record FundTransferRequest(String sourceAccountNumber, String targetAccountNumber, double amount, String pin,
        Long payeeId) {

    public FundTransferRequest(String sourceAccountNumber, String targetAccountNumber, double amount, String pin) {
        this(sourceAccountNumber, targetAccountNumber, amount, pin, null);
    }
}
