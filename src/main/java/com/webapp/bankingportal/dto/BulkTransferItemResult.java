package com.webapp.bankingportal.dto;

public record BulkTransferItemResult(
        int index,
        String targetAccountNumber,
        Double amount,
        Status status,
        String message,
        Long transactionId) {

    public enum Status {
        SUCCESS,
        FAILED,
        SKIPPED
    }

}
