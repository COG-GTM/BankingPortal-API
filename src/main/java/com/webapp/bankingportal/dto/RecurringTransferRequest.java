package com.webapp.bankingportal.dto;

import java.time.LocalDate;

import com.webapp.bankingportal.entity.TransferFrequency;

public record RecurringTransferRequest(
        String targetAccountNumber,
        double amount,
        String pin,
        TransferFrequency frequency,
        LocalDate startDate,
        LocalDate endDate) {
}
