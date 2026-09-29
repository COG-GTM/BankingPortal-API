package com.webapp.bankingportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.webapp.bankingportal.dto.RecurringTransferRequest;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.RecurrenceFrequency;
import com.webapp.bankingportal.entity.RecurringTransfer;
import com.webapp.bankingportal.entity.RecurringTransferStatus;
import com.webapp.bankingportal.exception.InsufficientBalanceException;
import com.webapp.bankingportal.exception.NotFoundException;
import com.webapp.bankingportal.exception.RecurringTransferException;
import com.webapp.bankingportal.exception.UnauthorizedException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.RecurringTransferRepository;
import com.webapp.bankingportal.service.AccountService;
import com.webapp.bankingportal.service.RecurringTransferServiceImpl;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RecurringTransferServiceTests {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-01");
    private static final String SOURCE = "src001";
    private static final String TARGET = "tgt001";
    private static final String PIN = "1234";
    private static final long ID = 42L;

    @Mock
    private RecurringTransferRepository recurringTransferRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountService accountService;

    private RecurringTransferServiceImpl service;

    private Account sourceAccount;
    private Account targetAccount;

    @BeforeEach
    public void setUp() {
        var clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new RecurringTransferServiceImpl(recurringTransferRepository, accountRepository,
                accountService, clock);

        sourceAccount = account(SOURCE);
        targetAccount = account(TARGET);
        when(accountRepository.findByAccountNumber(SOURCE)).thenReturn(sourceAccount);
        when(accountRepository.findByAccountNumber(TARGET)).thenReturn(targetAccount);
        when(recurringTransferRepository.save(any(RecurringTransfer.class)))
                .thenAnswer(AdditionalAnswers.returnsFirstArg());
    }

    private static Account account(String accountNumber) {
        var account = new Account();
        account.setAccountNumber(accountNumber);
        return account;
    }

    private static LocalDate date(String iso) {
        return LocalDate.parse(iso);
    }

    private static RecurringTransferRequest request(RecurrenceFrequency frequency, String start, String end) {
        return new RecurringTransferRequest(TARGET, 500, PIN, frequency,
                start == null ? null : date(start), end == null ? null : date(end));
    }

    private RecurringTransfer existing(RecurrenceFrequency frequency, String start, String end,
            String nextExecution, int processed, RecurringTransferStatus status) {
        var transfer = new RecurringTransfer();
        transfer.setId(ID);
        transfer.setSourceAccount(sourceAccount);
        transfer.setTargetAccount(targetAccount);
        transfer.setAmount(500);
        transfer.setFrequency(frequency);
        transfer.setStartDate(date(start));
        transfer.setEndDate(date(end));
        transfer.setNextExecutionDate(nextExecution == null ? null : date(nextExecution));
        transfer.setOccurrencesProcessed(processed);
        transfer.setStatus(status);
        when(recurringTransferRepository.findById(ID)).thenReturn(Optional.of(transfer));
        when(recurringTransferRepository.findByIdAndSourceAccount_AccountNumber(ID, SOURCE))
                .thenReturn(Optional.of(transfer));
        return transfer;
    }

    // --- creation ---

    @Test
    public void test_create_weekly_recurring_transfer() {
        var response = service.createRecurringTransfer(SOURCE,
                request(RecurrenceFrequency.WEEKLY, "2026-10-05", "2026-12-28"));

        verify(accountService).validateFundTransfer(SOURCE, TARGET, PIN, 500);
        assertEquals(RecurringTransferStatus.ACTIVE, response.status());
        assertEquals(SOURCE, response.sourceAccountNumber());
        assertEquals(TARGET, response.targetAccountNumber());
        assertEquals(date("2026-10-05"), response.nextExecutionDate());
        assertEquals(List.of(date("2026-10-05"), date("2026-10-12"), date("2026-10-19"),
                date("2026-10-26"), date("2026-11-02")), response.upcomingExecutionDates());
    }

    @Test
    public void test_create_monthly_recurring_transfer_starting_today() {
        var response = service.createRecurringTransfer(SOURCE,
                request(RecurrenceFrequency.MONTHLY, "2026-10-01", "2026-12-01"));

        assertEquals(date("2026-10-01"), response.nextExecutionDate());
        assertEquals(List.of(date("2026-10-01"), date("2026-11-01"), date("2026-12-01")),
                response.upcomingExecutionDates());
    }

    @Test
    public void test_create_rejects_invalid_schedules() {
        var invalid = List.of(
                request(null, "2026-10-05", "2026-12-28"),
                request(RecurrenceFrequency.WEEKLY, null, "2026-12-28"),
                request(RecurrenceFrequency.WEEKLY, "2026-10-05", null),
                request(RecurrenceFrequency.WEEKLY, "2026-09-30", "2026-12-28"),
                request(RecurrenceFrequency.MONTHLY, "2026-10-05", "2026-10-04"),
                request(RecurrenceFrequency.MONTHLY, "2026-10-05", "2031-10-06"));

        for (var req : invalid) {
            assertThrows(RecurringTransferException.class, () -> service.createRecurringTransfer(SOURCE, req));
        }
        verify(accountService, never()).validateFundTransfer(anyString(), anyString(), anyString(), anyDouble());
        verify(recurringTransferRepository, never()).save(any());
    }

    @Test
    public void test_create_propagates_transfer_validation_failure() {
        doThrow(new UnauthorizedException("Invalid PIN")).when(accountService)
                .validateFundTransfer(SOURCE, TARGET, PIN, 500);

        assertThrows(UnauthorizedException.class, () -> service.createRecurringTransfer(SOURCE,
                request(RecurrenceFrequency.WEEKLY, "2026-10-05", "2026-12-28")));
        verify(recurringTransferRepository, never()).save(any());
    }

    // --- cancellation ---

    @Test
    public void test_cancel_active_recurring_transfer() {
        var transfer = existing(RecurrenceFrequency.WEEKLY, "2026-10-05", "2026-12-28", "2026-10-05", 0,
                RecurringTransferStatus.ACTIVE);

        var response = service.cancelRecurringTransfer(SOURCE, ID);

        assertEquals(RecurringTransferStatus.CANCELLED, response.status());
        assertNull(response.nextExecutionDate());
        assertTrue(response.upcomingExecutionDates().isEmpty());
        assertNotNull(transfer.getCancelledAt());
        verify(recurringTransferRepository).save(transfer);
    }

    @Test
    public void test_cancel_recurring_transfer_of_another_account() {
        when(recurringTransferRepository.findByIdAndSourceAccount_AccountNumber(ID, "other1"))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.cancelRecurringTransfer("other1", ID));
        verify(recurringTransferRepository, never()).save(any());
    }

    @Test
    public void test_cancel_already_cancelled_or_completed_transfer() {
        for (var status : List.of(RecurringTransferStatus.CANCELLED, RecurringTransferStatus.COMPLETED)) {
            existing(RecurrenceFrequency.WEEKLY, "2026-10-05", "2026-12-28", null, 0, status);

            assertThrows(RecurringTransferException.class, () -> service.cancelRecurringTransfer(SOURCE, ID));
        }
        verify(recurringTransferRepository, never()).save(any());
    }

    @Test
    public void test_cancelled_transfer_is_not_executed() {
        existing(RecurrenceFrequency.WEEKLY, "2026-10-05", "2026-12-28", "2026-10-05", 0,
                RecurringTransferStatus.ACTIVE);
        service.cancelRecurringTransfer(SOURCE, ID);

        service.executeOccurrence(ID, date("2026-10-05"), date("2026-10-05"));

        verify(accountService, never()).scheduledFundTransfer(anyString(), anyString(), anyDouble());
    }

    // --- execution ---

    @Test
    public void test_execute_due_occurrence_advances_to_next_week() {
        var transfer = existing(RecurrenceFrequency.WEEKLY, "2026-10-05", "2026-12-28", "2026-10-05", 0,
                RecurringTransferStatus.ACTIVE);

        service.executeOccurrence(ID, date("2026-10-05"), date("2026-10-05"));

        verify(accountService).scheduledFundTransfer(SOURCE, TARGET, 500);
        assertEquals(date("2026-10-12"), transfer.getNextExecutionDate());
        assertEquals(1, transfer.getOccurrencesProcessed());
        assertEquals(date("2026-10-05"), transfer.getLastExecutionDate());
        assertEquals(RecurringTransferStatus.ACTIVE, transfer.getStatus());
    }

    @Test
    public void test_execute_final_occurrence_completes_schedule() {
        var transfer = existing(RecurrenceFrequency.MONTHLY, "2026-10-31", "2026-12-31", "2026-12-31", 2,
                RecurringTransferStatus.ACTIVE);

        service.executeOccurrence(ID, date("2026-12-31"), date("2026-12-31"));

        verify(accountService).scheduledFundTransfer(SOURCE, TARGET, 500);
        assertNull(transfer.getNextExecutionDate());
        assertEquals(RecurringTransferStatus.COMPLETED, transfer.getStatus());
    }

    @Test
    public void test_execute_skips_missed_occurrences() {
        var transfer = existing(RecurrenceFrequency.WEEKLY, "2026-10-05", "2026-12-28", "2026-10-05", 0,
                RecurringTransferStatus.ACTIVE);

        service.executeOccurrence(ID, date("2026-10-05"), date("2026-10-20"));

        verify(accountService).scheduledFundTransfer(SOURCE, TARGET, 500);
        assertEquals(date("2026-10-26"), transfer.getNextExecutionDate());
        assertEquals(3, transfer.getOccurrencesProcessed());
    }

    @Test
    public void test_execute_ignores_stale_or_future_occurrence() {
        existing(RecurrenceFrequency.WEEKLY, "2026-10-05", "2026-12-28", "2026-10-12", 1,
                RecurringTransferStatus.ACTIVE);

        service.executeOccurrence(ID, date("2026-10-05"), date("2026-10-12"));
        service.executeOccurrence(ID, date("2026-10-12"), date("2026-10-11"));

        verify(accountService, never()).scheduledFundTransfer(anyString(), anyString(), anyDouble());
        verify(recurringTransferRepository, never()).save(any());
    }

    @Test
    public void test_execute_propagates_transfer_failure_without_advancing() {
        var transfer = existing(RecurrenceFrequency.WEEKLY, "2026-10-05", "2026-12-28", "2026-10-05", 0,
                RecurringTransferStatus.ACTIVE);
        doThrow(new InsufficientBalanceException("Insufficient balance")).when(accountService)
                .scheduledFundTransfer(SOURCE, TARGET, 500);

        assertThrows(InsufficientBalanceException.class,
                () -> service.executeOccurrence(ID, date("2026-10-05"), date("2026-10-05")));
        assertEquals(date("2026-10-05"), transfer.getNextExecutionDate());
        verify(recurringTransferRepository, never()).save(any());
    }

    @Test
    public void test_record_failed_occurrence_advances_schedule() {
        var transfer = existing(RecurrenceFrequency.WEEKLY, "2026-10-05", "2026-12-28", "2026-10-05", 0,
                RecurringTransferStatus.ACTIVE);

        service.recordFailedOccurrence(ID, date("2026-10-05"), date("2026-10-05"), "Insufficient balance");

        assertEquals("Insufficient balance", transfer.getLastFailureReason());
        assertEquals(date("2026-10-12"), transfer.getNextExecutionDate());
        assertNull(transfer.getLastExecutionDate());
        assertEquals(RecurringTransferStatus.ACTIVE, transfer.getStatus());
    }

    @Test
    public void test_record_failed_occurrence_ignores_already_advanced_transfer() {
        var transfer = existing(RecurrenceFrequency.WEEKLY, "2026-10-05", "2026-12-28", "2026-10-12", 1,
                RecurringTransferStatus.ACTIVE);

        service.recordFailedOccurrence(ID, date("2026-10-05"), date("2026-10-05"), "boom");

        assertNull(transfer.getLastFailureReason());
        assertEquals(date("2026-10-12"), transfer.getNextExecutionDate());
        verify(recurringTransferRepository, never()).save(any());
    }

    @Test
    public void test_upcoming_dates_reflect_processed_occurrences() {
        existing(RecurrenceFrequency.WEEKLY, "2026-10-05", "2026-10-26", "2026-10-19", 2,
                RecurringTransferStatus.ACTIVE);

        var response = service.getRecurringTransfer(SOURCE, ID);

        assertEquals(List.of(date("2026-10-19"), date("2026-10-26")), response.upcomingExecutionDates());
    }

}
