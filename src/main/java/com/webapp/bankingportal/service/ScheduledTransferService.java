package com.webapp.bankingportal.service;

import java.time.LocalDateTime;
import java.util.List;

import com.webapp.bankingportal.dto.ScheduledTransferRequest;
import com.webapp.bankingportal.entity.ScheduledTransfer;

public interface ScheduledTransferService {

    public ScheduledTransfer scheduleTransfer(String sourceAccountNumber, ScheduledTransferRequest request);

    public List<ScheduledTransfer> getScheduledTransfers(String sourceAccountNumber);

    public void cancelScheduledTransfer(String sourceAccountNumber, Long scheduledTransferId);

    /**
     * Executes every pending transfer whose scheduled time is at or before
     * {@code now}. Each transfer runs in its own transaction, so one failure
     * does not affect the others.
     *
     * @return the number of transfers that completed successfully
     */
    public int executeDueTransfers(LocalDateTime now);

}
