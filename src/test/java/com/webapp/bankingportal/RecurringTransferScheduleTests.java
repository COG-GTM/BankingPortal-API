package com.webapp.bankingportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.webapp.bankingportal.entity.RecurrenceFrequency;
import com.webapp.bankingportal.util.RecurringTransferSchedule;

public class RecurringTransferScheduleTests {

    private static final int NO_LIMIT = Integer.MAX_VALUE;

    private static LocalDate date(String iso) {
        return LocalDate.parse(iso);
    }

    @Test
    public void test_weekly_schedule_includes_start_and_end_dates() {
        var dates = RecurringTransferSchedule.generate(date("2026-10-05"), date("2026-11-02"),
                RecurrenceFrequency.WEEKLY, NO_LIMIT);

        assertEquals(List.of(date("2026-10-05"), date("2026-10-12"), date("2026-10-19"),
                date("2026-10-26"), date("2026-11-02")), dates);
    }

    @Test
    public void test_weekly_schedule_stops_before_unaligned_end_date() {
        var dates = RecurringTransferSchedule.generate(date("2026-10-05"), date("2026-11-01"),
                RecurrenceFrequency.WEEKLY, NO_LIMIT);

        assertEquals(4, dates.size());
        assertEquals(date("2026-10-26"), dates.get(dates.size() - 1));
    }

    @Test
    public void test_weekly_schedule_crosses_year_boundary() {
        var dates = RecurringTransferSchedule.generate(date("2026-12-24"), date("2027-01-14"),
                RecurrenceFrequency.WEEKLY, NO_LIMIT);

        assertEquals(List.of(date("2026-12-24"), date("2026-12-31"), date("2027-01-07"),
                date("2027-01-14")), dates);
    }

    @Test
    public void test_monthly_schedule_on_same_day_of_month() {
        var dates = RecurringTransferSchedule.generate(date("2026-10-15"), date("2027-01-15"),
                RecurrenceFrequency.MONTHLY, NO_LIMIT);

        assertEquals(List.of(date("2026-10-15"), date("2026-11-15"), date("2026-12-15"),
                date("2027-01-15")), dates);
    }

    @Test
    public void test_monthly_schedule_clamps_to_month_end_without_drifting() {
        var dates = RecurringTransferSchedule.generate(date("2027-01-31"), date("2027-05-31"),
                RecurrenceFrequency.MONTHLY, NO_LIMIT);

        assertEquals(List.of(date("2027-01-31"), date("2027-02-28"), date("2027-03-31"),
                date("2027-04-30"), date("2027-05-31")), dates);
    }

    @Test
    public void test_monthly_schedule_uses_leap_day() {
        var dates = RecurringTransferSchedule.generate(date("2028-01-31"), date("2028-03-31"),
                RecurrenceFrequency.MONTHLY, NO_LIMIT);

        assertEquals(List.of(date("2028-01-31"), date("2028-02-29"), date("2028-03-31")), dates);
    }

    @Test
    public void test_schedule_with_same_start_and_end_has_single_occurrence() {
        for (var frequency : RecurrenceFrequency.values()) {
            var dates = RecurringTransferSchedule.generate(date("2026-10-05"), date("2026-10-05"),
                    frequency, NO_LIMIT);

            assertEquals(List.of(date("2026-10-05")), dates);
        }
    }

    @Test
    public void test_schedule_with_end_before_start_is_empty() {
        var dates = RecurringTransferSchedule.generate(date("2026-10-05"), date("2026-10-04"),
                RecurrenceFrequency.WEEKLY, NO_LIMIT);

        assertTrue(dates.isEmpty());
    }

    @Test
    public void test_schedule_with_missing_inputs_is_empty() {
        assertTrue(RecurringTransferSchedule.generate(null, date("2026-10-05"),
                RecurrenceFrequency.WEEKLY, NO_LIMIT).isEmpty());
        assertTrue(RecurringTransferSchedule.generate(date("2026-10-05"), null,
                RecurrenceFrequency.WEEKLY, NO_LIMIT).isEmpty());
        assertTrue(RecurringTransferSchedule.generate(date("2026-10-05"), date("2026-11-05"),
                null, NO_LIMIT).isEmpty());
    }

    @Test
    public void test_schedule_respects_limit() {
        var dates = RecurringTransferSchedule.generate(date("2026-10-05"), date("2031-10-05"),
                RecurrenceFrequency.WEEKLY, 3);

        assertEquals(List.of(date("2026-10-05"), date("2026-10-12"), date("2026-10-19")), dates);
    }

    @Test
    public void test_generate_from_offset_returns_remaining_occurrences() {
        var dates = RecurringTransferSchedule.generateFrom(date("2026-10-15"), date("2027-01-15"),
                RecurrenceFrequency.MONTHLY, 2, NO_LIMIT);

        assertEquals(List.of(date("2026-12-15"), date("2027-01-15")), dates);
    }

    @Test
    public void test_first_index_after_date() {
        var start = date("2026-10-05");

        assertEquals(0, RecurringTransferSchedule.firstIndexAfter(start, RecurrenceFrequency.WEEKLY,
                date("2026-10-04")));
        assertEquals(1, RecurringTransferSchedule.firstIndexAfter(start, RecurrenceFrequency.WEEKLY,
                date("2026-10-05")));
        assertEquals(3, RecurringTransferSchedule.firstIndexAfter(start, RecurrenceFrequency.WEEKLY,
                date("2026-10-20")));
        assertEquals(2, RecurringTransferSchedule.firstIndexAfter(start, RecurrenceFrequency.MONTHLY,
                date("2026-11-05")));
    }

    @Test
    public void test_occurrence_or_null_past_end_date() {
        var start = date("2026-10-05");
        var end = date("2026-10-19");

        assertEquals(date("2026-10-19"), RecurringTransferSchedule.occurrenceOrNull(start, end,
                RecurrenceFrequency.WEEKLY, 2));
        assertNull(RecurringTransferSchedule.occurrenceOrNull(start, end, RecurrenceFrequency.WEEKLY, 3));
    }

}
