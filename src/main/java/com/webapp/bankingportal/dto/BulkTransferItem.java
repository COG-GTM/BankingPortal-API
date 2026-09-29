package com.webapp.bankingportal.dto;

public record BulkTransferItem(String targetAccountNumber, double amount) {
}
