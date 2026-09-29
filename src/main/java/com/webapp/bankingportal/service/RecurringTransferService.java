package com.webapp.bankingportal.service;

import java.time.LocalDate;
import java.util.List;

import com.webapp.bankingportal.dto.RecurringTransferRequest;
import com.webapp.bankingportal.entity.RecurringTransfer;

public interface RecurringTransferService {

    RecurringTransfer createRecurringTransfer(String sourceAccountNumber, RecurringTransferRequest request);

    List<RecurringTransfer> getRecurringTransfers(String sourceAccountNumber);

    RecurringTransfer cancelRecurringTransfer(String sourceAccountNumber, Long recurringTransferId);

    /**
     * Executes every active schedule whose next execution date is on or before
     * {@code today}.
     *
     * @return the number of schedules processed
     */
    int executeDueTransfers(LocalDate today);
}
