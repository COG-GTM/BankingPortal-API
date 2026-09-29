package com.webapp.bankingportal.service;

import java.time.LocalDate;
import java.util.List;

import com.webapp.bankingportal.dto.DueRecurringTransfer;
import com.webapp.bankingportal.dto.RecurringTransferRequest;
import com.webapp.bankingportal.dto.RecurringTransferResponse;

public interface RecurringTransferService {

    RecurringTransferResponse createRecurringTransfer(String sourceAccountNumber, RecurringTransferRequest request);

    List<RecurringTransferResponse> getRecurringTransfers(String sourceAccountNumber);

    RecurringTransferResponse getRecurringTransfer(String sourceAccountNumber, Long id);

    RecurringTransferResponse cancelRecurringTransfer(String sourceAccountNumber, Long id);

    List<DueRecurringTransfer> findDueTransfers(LocalDate date);

    /**
     * Executes the occurrence scheduled on {@code occurrenceDate} and advances
     * the schedule past {@code date}. Occurrences missed while the scheduler was
     * not running are skipped rather than replayed. No-op if the transfer is no
     * longer active or has already moved past that occurrence.
     */
    void executeOccurrence(Long id, LocalDate occurrenceDate, LocalDate date);

    /**
     * Records a failed attempt for the occurrence scheduled on
     * {@code occurrenceDate} and advances the schedule. No-op if the transfer
     * has already moved past that occurrence.
     */
    void recordFailedOccurrence(Long id, LocalDate occurrenceDate, LocalDate date, String reason);

}
