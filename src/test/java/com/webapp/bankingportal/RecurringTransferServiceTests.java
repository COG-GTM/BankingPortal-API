package com.webapp.bankingportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.webapp.bankingportal.dto.RecurringTransferRequest;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.RecurringTransfer;
import com.webapp.bankingportal.entity.RecurringTransferStatus;
import com.webapp.bankingportal.entity.TransferFrequency;
import com.webapp.bankingportal.exception.FundTransferException;
import com.webapp.bankingportal.exception.InsufficientBalanceException;
import com.webapp.bankingportal.exception.InvalidAmountException;
import com.webapp.bankingportal.exception.NotFoundException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.RecurringTransferRepository;
import com.webapp.bankingportal.service.AccountService;
import com.webapp.bankingportal.service.RecurringTransferServiceImpl;

import lombok.val;

@ExtendWith(MockitoExtension.class)
public class RecurringTransferServiceTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    private static final String SOURCE = "111111";
    private static final String TARGET = "222222";
    private static final String PIN = "1234";

    @Mock
    RecurringTransferRepository recurringTransferRepository;

    @Mock
    AccountRepository accountRepository;

    @Mock
    AccountService accountService;

    RecurringTransferServiceImpl service;

    @BeforeEach
    void setUp() {
        val clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new RecurringTransferServiceImpl(recurringTransferRepository, accountRepository, accountService,
                clock);
    }

    private static Account account(String accountNumber) {
        val account = new Account();
        account.setAccountNumber(accountNumber);
        return account;
    }

    private static RecurringTransfer activeTransfer(TransferFrequency frequency, LocalDate start, LocalDate end,
            LocalDate next) {
        val transfer = new RecurringTransfer();
        transfer.setId(1L);
        transfer.setSourceAccount(account(SOURCE));
        transfer.setTargetAccount(account(TARGET));
        transfer.setAmount(500);
        transfer.setFrequency(frequency);
        transfer.setStartDate(start);
        transfer.setEndDate(end);
        transfer.setNextExecutionDate(next);
        transfer.setStatus(RecurringTransferStatus.ACTIVE);
        return transfer;
    }

    private void stubDue(RecurringTransfer... transfers) {
        when(recurringTransferRepository.findByStatusAndNextExecutionDateLessThanEqual(
                any(), any())).thenReturn(List.of(transfers));
    }

    // --- creation ---

    @Test
    public void test_create_recurring_transfer_with_valid_details() {
        when(accountRepository.findByAccountNumber(SOURCE)).thenReturn(account(SOURCE));
        when(accountRepository.findByAccountNumber(TARGET)).thenReturn(account(TARGET));
        when(recurringTransferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        val request = new RecurringTransferRequest(TARGET, 500, PIN, TransferFrequency.WEEKLY,
                TODAY.plusDays(3), TODAY.plusMonths(2));
        val created = service.createRecurringTransfer(SOURCE, request);

        verify(accountService).validateFundTransfer(SOURCE, TARGET, PIN, 500);
        assertEquals(RecurringTransferStatus.ACTIVE, created.getStatus());
        assertEquals(TODAY.plusDays(3), created.getNextExecutionDate());
        assertEquals(SOURCE, created.getSourceAccount().getAccountNumber());
        assertEquals(TARGET, created.getTargetAccount().getAccountNumber());
        assertEquals(0, created.getOccurrencesProcessed());
    }

    @Test
    public void test_create_recurring_transfer_starting_today() {
        when(recurringTransferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        val created = service.createRecurringTransfer(SOURCE,
                new RecurringTransferRequest(TARGET, 500, PIN, TransferFrequency.MONTHLY, TODAY, TODAY));

        assertEquals(TODAY, created.getNextExecutionDate());
    }

    @Test
    public void test_create_recurring_transfer_with_end_before_start() {
        val request = new RecurringTransferRequest(TARGET, 500, PIN, TransferFrequency.WEEKLY,
                TODAY.plusDays(10), TODAY.plusDays(9));

        assertThrows(FundTransferException.class, () -> service.createRecurringTransfer(SOURCE, request));
        verify(recurringTransferRepository, never()).save(any());
    }

    @Test
    public void test_create_recurring_transfer_with_start_in_past() {
        val request = new RecurringTransferRequest(TARGET, 500, PIN, TransferFrequency.WEEKLY,
                TODAY.minusDays(1), TODAY.plusMonths(1));

        assertThrows(FundTransferException.class, () -> service.createRecurringTransfer(SOURCE, request));
        verify(recurringTransferRepository, never()).save(any());
    }

    @Test
    public void test_create_recurring_transfer_with_missing_frequency_or_dates() {
        assertThrows(FundTransferException.class, () -> service.createRecurringTransfer(SOURCE,
                new RecurringTransferRequest(TARGET, 500, PIN, null, TODAY, TODAY.plusMonths(1))));
        assertThrows(FundTransferException.class, () -> service.createRecurringTransfer(SOURCE,
                new RecurringTransferRequest(TARGET, 500, PIN, TransferFrequency.WEEKLY, null, TODAY)));
        assertThrows(FundTransferException.class, () -> service.createRecurringTransfer(SOURCE,
                new RecurringTransferRequest(TARGET, 500, PIN, TransferFrequency.WEEKLY, TODAY, null)));
        verify(recurringTransferRepository, never()).save(any());
    }

    @Test
    public void test_create_recurring_transfer_propagates_transfer_validation_errors() {
        doThrow(new InvalidAmountException("Amount must be in multiples of 100"))
                .when(accountService).validateFundTransfer(SOURCE, TARGET, PIN, 150);

        assertThrows(InvalidAmountException.class, () -> service.createRecurringTransfer(SOURCE,
                new RecurringTransferRequest(TARGET, 150, PIN, TransferFrequency.WEEKLY, TODAY, TODAY)));
        verify(recurringTransferRepository, never()).save(any());
    }

    // --- execution ---

    @Test
    public void test_execute_due_weekly_transfer_advances_to_next_week() {
        val transfer = activeTransfer(TransferFrequency.WEEKLY, TODAY, TODAY.plusWeeks(4), TODAY);
        stubDue(transfer);

        val processed = service.executeDueTransfers(TODAY);

        assertEquals(1, processed);
        verify(accountService).executeScheduledFundTransfer(SOURCE, TARGET, 500);
        verify(recurringTransferRepository).save(transfer);
        assertEquals(TODAY.plusWeeks(1), transfer.getNextExecutionDate());
        assertEquals(RecurringTransferStatus.ACTIVE, transfer.getStatus());
        assertEquals(1, transfer.getOccurrencesProcessed());
        assertEquals(0, transfer.getFailedOccurrences());
    }

    @Test
    public void test_execute_due_monthly_transfer_keeps_anchor_day() {
        val start = LocalDate.of(2027, 1, 31);
        val transfer = activeTransfer(TransferFrequency.MONTHLY, start, LocalDate.of(2027, 12, 31),
                LocalDate.of(2027, 2, 28));
        stubDue(transfer);

        service.executeDueTransfers(LocalDate.of(2027, 2, 28));

        assertEquals(LocalDate.of(2027, 3, 31), transfer.getNextExecutionDate());
    }

    @Test
    public void test_execute_last_occurrence_completes_schedule() {
        val transfer = activeTransfer(TransferFrequency.WEEKLY, TODAY.minusWeeks(2), TODAY.plusDays(6), TODAY);
        stubDue(transfer);

        service.executeDueTransfers(TODAY);

        verify(accountService).executeScheduledFundTransfer(SOURCE, TARGET, 500);
        assertEquals(RecurringTransferStatus.COMPLETED, transfer.getStatus());
        assertNull(transfer.getNextExecutionDate());
    }

    @Test
    public void test_execute_failed_transfer_records_failure_and_advances() {
        val transfer = activeTransfer(TransferFrequency.MONTHLY, TODAY, TODAY.plusMonths(3), TODAY);
        stubDue(transfer);
        doThrow(new InsufficientBalanceException("Insufficient balance"))
                .when(accountService).executeScheduledFundTransfer(SOURCE, TARGET, 500);

        service.executeDueTransfers(TODAY);

        assertEquals(1, transfer.getFailedOccurrences());
        assertEquals(1, transfer.getOccurrencesProcessed());
        assertEquals("Insufficient balance", transfer.getLastFailureReason());
        assertEquals(TODAY.plusMonths(1), transfer.getNextExecutionDate());
        assertEquals(RecurringTransferStatus.ACTIVE, transfer.getStatus());
        verify(recurringTransferRepository).save(transfer);
    }

    @Test
    public void test_execute_successful_transfer_clears_previous_failure() {
        val transfer = activeTransfer(TransferFrequency.WEEKLY, TODAY, TODAY.plusWeeks(4), TODAY);
        transfer.setLastFailureReason("Insufficient balance");
        stubDue(transfer);

        service.executeDueTransfers(TODAY);

        assertNull(transfer.getLastFailureReason());
    }

    @Test
    public void test_execute_missed_occurrences_runs_once_and_skips_to_future() {
        val start = TODAY.minusWeeks(3);
        val transfer = activeTransfer(TransferFrequency.WEEKLY, start, TODAY.plusMonths(2), start);
        stubDue(transfer);

        service.executeDueTransfers(TODAY.plusDays(2));

        verify(accountService, times(1)).executeScheduledFundTransfer(anyString(), anyString(), anyDouble());
        assertEquals(TODAY.plusWeeks(1), transfer.getNextExecutionDate());
    }

    @Test
    public void test_execute_one_failure_does_not_block_other_schedules() {
        val failing = activeTransfer(TransferFrequency.WEEKLY, TODAY, TODAY.plusWeeks(4), TODAY);
        val succeeding = activeTransfer(TransferFrequency.WEEKLY, TODAY, TODAY.plusWeeks(4), TODAY);
        succeeding.setId(2L);
        succeeding.setAmount(200);
        stubDue(failing, succeeding);
        doThrow(new NotFoundException("Account does not exist"))
                .when(accountService).executeScheduledFundTransfer(SOURCE, TARGET, 500);

        val processed = service.executeDueTransfers(TODAY);

        assertEquals(2, processed);
        verify(accountService).executeScheduledFundTransfer(SOURCE, TARGET, 200);
        assertEquals(1, failing.getFailedOccurrences());
        assertEquals(0, succeeding.getFailedOccurrences());
    }

    @Test
    public void test_execute_queries_only_active_due_schedules() {
        stubDue();

        assertEquals(0, service.executeDueTransfers(TODAY));

        verify(recurringTransferRepository)
                .findByStatusAndNextExecutionDateLessThanEqual(RecurringTransferStatus.ACTIVE, TODAY);
        verify(accountService, never()).executeScheduledFundTransfer(anyString(), anyString(), anyDouble());
    }

    // --- cancellation ---

    @Test
    public void test_cancel_recurring_transfer() {
        val transfer = activeTransfer(TransferFrequency.WEEKLY, TODAY, TODAY.plusWeeks(4), TODAY);
        when(recurringTransferRepository.findById(1L)).thenReturn(Optional.of(transfer));
        when(recurringTransferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        val cancelled = service.cancelRecurringTransfer(SOURCE, 1L);

        assertEquals(RecurringTransferStatus.CANCELLED, cancelled.getStatus());
        assertNull(cancelled.getNextExecutionDate());
    }

    @Test
    public void test_cancel_recurring_transfer_of_other_account() {
        val transfer = activeTransfer(TransferFrequency.WEEKLY, TODAY, TODAY.plusWeeks(4), TODAY);
        when(recurringTransferRepository.findById(1L)).thenReturn(Optional.of(transfer));

        assertThrows(NotFoundException.class, () -> service.cancelRecurringTransfer("999999", 1L));
        verify(recurringTransferRepository, never()).save(any());
    }

    @Test
    public void test_cancel_inactive_recurring_transfer() {
        val transfer = activeTransfer(TransferFrequency.WEEKLY, TODAY, TODAY.plusWeeks(4), null);
        transfer.setStatus(RecurringTransferStatus.COMPLETED);
        when(recurringTransferRepository.findById(1L)).thenReturn(Optional.of(transfer));

        assertThrows(FundTransferException.class, () -> service.cancelRecurringTransfer(SOURCE, 1L));
    }
}
