package com.webapp.bankingportal.dto;

import java.time.LocalDate;

import com.webapp.bankingportal.entity.RecurrenceFrequency;

public record RecurringTransferRequest(
        String targetAccountNumber,
        double amount,
        String pin,
        RecurrenceFrequency frequency,
        LocalDate startDate,
        LocalDate endDate) {
}
