package com.webapp.bankingportal.dto;

import com.webapp.bankingportal.entity.RecurringTransfer;

public record RecurringTransferResponse(
        Long id,
        String sourceAccountNumber,
        String targetAccountNumber,
        double amount,
        String frequency,
        String startDate,
        String endDate,
        String nextExecutionDate,
        int occurrencesProcessed,
        int failedOccurrences,
        String lastFailureReason,
        String status) {

    public static RecurringTransferResponse from(RecurringTransfer transfer) {
        return new RecurringTransferResponse(
                transfer.getId(),
                transfer.getSourceAccount().getAccountNumber(),
                transfer.getTargetAccount().getAccountNumber(),
                transfer.getAmount(),
                transfer.getFrequency().name(),
                transfer.getStartDate().toString(),
                transfer.getEndDate().toString(),
                transfer.getNextExecutionDate() == null ? null : transfer.getNextExecutionDate().toString(),
                transfer.getOccurrencesProcessed(),
                transfer.getFailedOccurrences(),
                transfer.getLastFailureReason(),
                transfer.getStatus().name());
    }
}
