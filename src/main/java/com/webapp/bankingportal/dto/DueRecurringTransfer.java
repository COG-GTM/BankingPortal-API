package com.webapp.bankingportal.dto;

import java.time.LocalDate;

public record DueRecurringTransfer(Long id, LocalDate occurrenceDate) {
}
