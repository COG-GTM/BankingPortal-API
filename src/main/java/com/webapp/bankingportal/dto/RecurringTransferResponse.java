package com.webapp.bankingportal.dto;

import java.time.LocalDate;
import java.util.List;

import com.webapp.bankingportal.entity.RecurrenceFrequency;
import com.webapp.bankingportal.entity.RecurringTransferStatus;

public record RecurringTransferResponse(
        Long id,
        String sourceAccountNumber,
        String targetAccountNumber,
        double amount,
        RecurrenceFrequency frequency,
        LocalDate startDate,
        LocalDate endDate,
        RecurringTransferStatus status,
        LocalDate nextExecutionDate,
        LocalDate lastExecutionDate,
        String lastFailureReason,
        List<LocalDate> upcomingExecutionDates) {
}
