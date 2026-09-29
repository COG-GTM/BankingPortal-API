package com.webapp.bankingportal.dto;

import java.util.List;

public record TransferPreviewResponse(
        String sourceAccountNumber,
        String targetAccountNumber,
        double amount,
        double fee,
        double totalDebit,
        double currentBalance,
        double resultingBalance,
        boolean executable,
        List<TransferIssue> errors,
        List<TransferIssue> warnings) {
}
