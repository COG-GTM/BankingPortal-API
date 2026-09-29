package com.webapp.bankingportal.dto;

import com.webapp.bankingportal.entity.ScheduledTransfer;

public record ScheduledTransferResponse(
        Long id,
        String sourceAccountNumber,
        String targetAccountNumber,
        double amount,
        String scheduledAt,
        String status,
        String failureReason,
        String createdAt,
        String executedAt) {

    public static ScheduledTransferResponse from(ScheduledTransfer transfer) {
        return new ScheduledTransferResponse(
                transfer.getId(),
                transfer.getSourceAccount().getAccountNumber(),
                transfer.getTargetAccount().getAccountNumber(),
                transfer.getAmount(),
                transfer.getScheduledAt().toString(),
                transfer.getStatus().name(),
                transfer.getFailureReason(),
                transfer.getCreatedAt().toString(),
                transfer.getExecutedAt() == null ? null : transfer.getExecutedAt().toString());
    }
}
