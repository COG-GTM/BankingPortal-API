package com.webapp.bankingportal.util;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.webapp.bankingportal.entity.RecurrenceFrequency;

import lombok.val;

/**
 * Pure date arithmetic for recurring transfer schedules. Schedules are
 * inclusive of both the start and end dates.
 */
public final class RecurringTransferSchedule {

    private RecurringTransferSchedule() {
    }

    /**
     * @return every occurrence in {@code [startDate, endDate]}, capped at
     *         {@code limit} entries.
     */
    public static List<LocalDate> generate(LocalDate startDate, LocalDate endDate,
            RecurrenceFrequency frequency, int limit) {
        return generateFrom(startDate, endDate, frequency, 0, limit);
    }

    /**
     * @return occurrences starting at zero-based {@code fromIndex} that fall on or
     *         before {@code endDate}, capped at {@code limit} entries.
     */
    public static List<LocalDate> generateFrom(LocalDate startDate, LocalDate endDate,
            RecurrenceFrequency frequency, int fromIndex, int limit) {
        val dates = new ArrayList<LocalDate>();
        if (startDate == null || endDate == null || frequency == null || endDate.isBefore(startDate)) {
            return dates;
        }

        for (int index = fromIndex; dates.size() < limit; index++) {
            val date = frequency.occurrence(startDate, index);
            if (date.isAfter(endDate)) {
                break;
            }
            dates.add(date);
        }
        return dates;
    }

    /**
     * @return the zero-based index of the first occurrence strictly after
     *         {@code date}.
     */
    public static int firstIndexAfter(LocalDate startDate, RecurrenceFrequency frequency, LocalDate date) {
        int index = 0;
        while (!frequency.occurrence(startDate, index).isAfter(date)) {
            index++;
        }
        return index;
    }

    /**
     * @return the occurrence at {@code index}, or null if it falls after
     *         {@code endDate}.
     */
    public static LocalDate occurrenceOrNull(LocalDate startDate, LocalDate endDate,
            RecurrenceFrequency frequency, int index) {
        val date = frequency.occurrence(startDate, index);
        return date.isAfter(endDate) ? null : date;
    }

}
