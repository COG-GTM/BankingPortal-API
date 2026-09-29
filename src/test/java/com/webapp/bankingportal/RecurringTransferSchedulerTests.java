package com.webapp.bankingportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.webapp.bankingportal.dto.DueRecurringTransfer;
import com.webapp.bankingportal.exception.InsufficientBalanceException;
import com.webapp.bankingportal.scheduler.RecurringTransferScheduler;
import com.webapp.bankingportal.service.RecurringTransferService;

@ExtendWith(MockitoExtension.class)
public class RecurringTransferSchedulerTests {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-05");

    @Mock
    private RecurringTransferService recurringTransferService;

    private RecurringTransferScheduler scheduler;

    @BeforeEach
    public void setUp() {
        var clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        scheduler = new RecurringTransferScheduler(recurringTransferService, clock);
    }

    @Test
    public void test_run_uses_clock_date_and_executes_each_due_transfer() {
        when(recurringTransferService.findDueTransfers(TODAY)).thenReturn(List.of(
                new DueRecurringTransfer(1L, TODAY),
                new DueRecurringTransfer(2L, TODAY.minusDays(7))));

        scheduler.run();

        verify(recurringTransferService).executeOccurrence(1L, TODAY, TODAY);
        verify(recurringTransferService).executeOccurrence(2L, TODAY.minusDays(7), TODAY);
        verify(recurringTransferService, never()).recordFailedOccurrence(any(), any(), any(), anyString());
    }

    @Test
    public void test_failed_transfer_is_recorded_and_batch_continues() {
        when(recurringTransferService.findDueTransfers(TODAY)).thenReturn(List.of(
                new DueRecurringTransfer(1L, TODAY),
                new DueRecurringTransfer(2L, TODAY)));
        doThrow(new InsufficientBalanceException("Insufficient balance"))
                .when(recurringTransferService).executeOccurrence(1L, TODAY, TODAY);

        var executed = scheduler.processDueTransfers(TODAY);

        assertEquals(1, executed);
        verify(recurringTransferService).recordFailedOccurrence(1L, TODAY, TODAY, "Insufficient balance");
        verify(recurringTransferService).executeOccurrence(2L, TODAY, TODAY);
    }

    @Test
    public void test_failure_while_recording_does_not_stop_batch() {
        when(recurringTransferService.findDueTransfers(TODAY)).thenReturn(List.of(
                new DueRecurringTransfer(1L, TODAY),
                new DueRecurringTransfer(2L, TODAY)));
        doThrow(new IllegalStateException("boom"))
                .when(recurringTransferService).executeOccurrence(1L, TODAY, TODAY);
        doThrow(new IllegalStateException("db down"))
                .when(recurringTransferService).recordFailedOccurrence(1L, TODAY, TODAY, "boom");

        var executed = scheduler.processDueTransfers(TODAY);

        assertEquals(1, executed);
        verify(recurringTransferService).executeOccurrence(2L, TODAY, TODAY);
    }

    @Test
    public void test_no_due_transfers() {
        when(recurringTransferService.findDueTransfers(TODAY)).thenReturn(List.of());

        assertEquals(0, scheduler.processDueTransfers(TODAY));
        verify(recurringTransferService, never()).executeOccurrence(any(), any(), any());
    }

}
