package com.webapp.bankingportal.dto;

import java.time.Instant;

import com.webapp.bankingportal.entity.BalanceAuditLog;
import com.webapp.bankingportal.entity.TransactionType;

public record BalanceAuditLogResponse(
        Long id,
        String accountNumber,
        TransactionType operation,
        double amount,
        double balanceBefore,
        double balanceAfter,
        String performedBy,
        String counterpartyAccountNumber,
        Long transactionId,
        Instant createdAt) {

    public static BalanceAuditLogResponse from(BalanceAuditLog entry) {
        return new BalanceAuditLogResponse(
                entry.getId(),
                entry.getAccountNumber(),
                entry.getOperation(),
                entry.getAmount(),
                entry.getBalanceBefore(),
                entry.getBalanceAfter(),
                entry.getPerformedBy(),
                entry.getCounterpartyAccountNumber(),
                entry.getTransactionId(),
                entry.getCreatedAt());
    }

}
