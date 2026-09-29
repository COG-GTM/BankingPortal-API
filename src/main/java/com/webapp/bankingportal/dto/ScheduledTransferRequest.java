package com.webapp.bankingportal.dto;

import java.time.LocalDateTime;

public record ScheduledTransferRequest(String targetAccountNumber, double amount, LocalDateTime scheduledAt,
        String pin) {
}
