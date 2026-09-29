package com.webapp.bankingportal.dto;

import com.webapp.bankingportal.type.TransactionRuleViolation;
import com.webapp.bankingportal.type.TransferWarning;

public record TransferIssue(String code, String message) {

    public static TransferIssue of(TransactionRuleViolation violation) {
        return new TransferIssue(violation.name(), violation.getMessage());
    }

    public static TransferIssue of(TransferWarning warning) {
        return new TransferIssue(warning.name(), warning.getMessage());
    }
}
