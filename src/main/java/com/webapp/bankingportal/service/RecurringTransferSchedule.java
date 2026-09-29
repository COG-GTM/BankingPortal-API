package com.webapp.bankingportal.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.webapp.bankingportal.entity.TransferFrequency;

import lombok.val;

/**
 * Pure date arithmetic for recurring transfer schedules.
 *
 * Occurrence {@code n} is always computed from the start date (not from the
 * previous occurrence), so monthly schedules anchored on the 29th-31st clamp to
 * the last day of shorter months without drifting: Jan 31 -> Feb 28 -> Mar 31.
 */
public final class RecurringTransferSchedule {

    private RecurringTransferSchedule() {
    }

    public static LocalDate occurrence(LocalDate startDate, TransferFrequency frequency, long index) {
        if (index < 0) {
            throw new IllegalArgumentException("Occurrence index cannot be negative");
        }
        return switch (frequency) {
            case WEEKLY -> startDate.plusWeeks(index);
            case MONTHLY -> startDate.plusMonths(index);
        };
    }

    /** All occurrence dates in {@code [startDate, endDate]}, inclusive. */
    public static List<LocalDate> occurrences(LocalDate startDate, LocalDate endDate, TransferFrequency frequency) {
        val dates = new ArrayList<LocalDate>();
        for (long i = 0;; i++) {
            val date = occurrence(startDate, frequency, i);
            if (date.isAfter(endDate)) {
                return dates;
            }
            dates.add(date);
        }
    }

    /**
     * First occurrence strictly after {@code after} that is on or before
     * {@code endDate}, or empty if the schedule has no further occurrences.
     */
    public static Optional<LocalDate> nextOccurrenceAfter(LocalDate startDate, LocalDate endDate,
            TransferFrequency frequency, LocalDate after) {
        for (long i = 0;; i++) {
            val date = occurrence(startDate, frequency, i);
            if (date.isAfter(endDate)) {
                return Optional.empty();
            }
            if (date.isAfter(after)) {
                return Optional.of(date);
            }
        }
    }
}
