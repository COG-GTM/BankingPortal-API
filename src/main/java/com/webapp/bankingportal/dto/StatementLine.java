package com.webapp.bankingportal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.webapp.bankingportal.entity.TransactionType;

/**
 * A single transaction on an account statement. Exactly one of {@code debit}
 * or {@code credit} is non-null.
 */
public record StatementLine(
        Long transactionId,
        LocalDateTime date,
        TransactionType type,
        String description,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal balance) {
}
