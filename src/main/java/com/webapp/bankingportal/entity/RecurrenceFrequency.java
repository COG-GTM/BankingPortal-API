package com.webapp.bankingportal.entity;

import java.time.LocalDate;

public enum RecurrenceFrequency {
    WEEKLY {
        @Override
        public LocalDate occurrence(LocalDate startDate, int index) {
            return startDate.plusWeeks(index);
        }
    },
    MONTHLY {
        /**
         * Always offsets from the start date so a schedule anchored on the 31st
         * is clamped to shorter month ends without drifting permanently.
         */
        @Override
        public LocalDate occurrence(LocalDate startDate, int index) {
            return startDate.plusMonths(index);
        }
    };

    /**
     * @return the date of the zero-based {@code index}-th occurrence of a
     *         schedule beginning on {@code startDate}.
     */
    public abstract LocalDate occurrence(LocalDate startDate, int index);
}
