package com.webapp.bankingportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.webapp.bankingportal.entity.TransferFrequency;
import com.webapp.bankingportal.service.RecurringTransferSchedule;

import lombok.val;

public class RecurringTransferScheduleTests {

    @Test
    public void test_weekly_occurrences_include_start_and_end_date() {
        val dates = RecurringTransferSchedule.occurrences(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 29), TransferFrequency.WEEKLY);

        assertEquals(List.of(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 8),
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 22),
                LocalDate.of(2026, 10, 29)), dates);
    }

    @Test
    public void test_weekly_occurrences_stop_before_end_date_when_not_aligned() {
        val dates = RecurringTransferSchedule.occurrences(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14), TransferFrequency.WEEKLY);

        assertEquals(List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8)), dates);
    }

    @Test
    public void test_monthly_occurrences_keep_day_of_month() {
        val dates = RecurringTransferSchedule.occurrences(
                LocalDate.of(2026, 10, 15), LocalDate.of(2027, 1, 15), TransferFrequency.MONTHLY);

        assertEquals(List.of(
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 12, 15),
                LocalDate.of(2027, 1, 15)), dates);
    }

    @Test
    public void test_monthly_occurrences_clamp_to_month_end_without_drifting() {
        val dates = RecurringTransferSchedule.occurrences(
                LocalDate.of(2027, 1, 31), LocalDate.of(2027, 5, 31), TransferFrequency.MONTHLY);

        assertEquals(List.of(
                LocalDate.of(2027, 1, 31),
                LocalDate.of(2027, 2, 28),
                LocalDate.of(2027, 3, 31),
                LocalDate.of(2027, 4, 30),
                LocalDate.of(2027, 5, 31)), dates);
    }

    @Test
    public void test_monthly_occurrences_handle_leap_year() {
        val dates = RecurringTransferSchedule.occurrences(
                LocalDate.of(2028, 1, 30), LocalDate.of(2028, 3, 30), TransferFrequency.MONTHLY);

        assertEquals(List.of(
                LocalDate.of(2028, 1, 30),
                LocalDate.of(2028, 2, 29),
                LocalDate.of(2028, 3, 30)), dates);
    }

    @Test
    public void test_single_occurrence_when_start_equals_end() {
        val date = LocalDate.of(2026, 10, 1);
        assertEquals(List.of(date), RecurringTransferSchedule.occurrences(date, date, TransferFrequency.MONTHLY));
    }

    @Test
    public void test_no_occurrences_when_end_before_start() {
        assertTrue(RecurringTransferSchedule.occurrences(
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1), TransferFrequency.WEEKLY).isEmpty());
    }

    @Test
    public void test_next_occurrence_after_given_date() {
        val start = LocalDate.of(2026, 10, 1);
        val end = LocalDate.of(2026, 12, 31);

        assertEquals(Optional.of(LocalDate.of(2026, 10, 8)), RecurringTransferSchedule
                .nextOccurrenceAfter(start, end, TransferFrequency.WEEKLY, LocalDate.of(2026, 10, 1)));
        assertEquals(Optional.of(LocalDate.of(2026, 10, 15)), RecurringTransferSchedule
                .nextOccurrenceAfter(start, end, TransferFrequency.WEEKLY, LocalDate.of(2026, 10, 10)));
        assertEquals(Optional.of(LocalDate.of(2026, 10, 1)), RecurringTransferSchedule
                .nextOccurrenceAfter(start, end, TransferFrequency.WEEKLY, LocalDate.of(2026, 9, 30)));
    }

    @Test
    public void test_next_occurrence_empty_after_last_occurrence() {
        assertEquals(Optional.empty(), RecurringTransferSchedule.nextOccurrenceAfter(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 15), TransferFrequency.MONTHLY,
                LocalDate.of(2026, 12, 1)));
    }

    @Test
    public void test_negative_occurrence_index_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RecurringTransferSchedule.occurrence(LocalDate.of(2026, 10, 1), TransferFrequency.WEEKLY, -1));
    }
}
