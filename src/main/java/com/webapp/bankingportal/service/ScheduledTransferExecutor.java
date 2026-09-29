package com.webapp.bankingportal.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.webapp.bankingportal.entity.ScheduledTransferStatus;
import com.webapp.bankingportal.exception.NotFoundException;
import com.webapp.bankingportal.repository.ScheduledTransferRepository;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.RequiredArgsConstructor;
import lombok.val;

@Component
@RequiredArgsConstructor
public class ScheduledTransferExecutor {

    private final ScheduledTransferRepository scheduledTransferRepository;
    private final AccountService accountService;
    private final Clock clock;

    /**
     * @return true if the transfer was executed, false if it was no longer pending
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean execute(Long scheduledTransferId) {
        val transfer = scheduledTransferRepository.findById(scheduledTransferId)
                .orElseThrow(() -> new NotFoundException(ApiMessages.SCHEDULED_TRANSFER_NOT_FOUND.getMessage()));

        if (transfer.getStatus() != ScheduledTransferStatus.PENDING) {
            return false;
        }

        accountService.transferFunds(
                transfer.getSourceAccount().getAccountNumber(),
                transfer.getTargetAccount().getAccountNumber(),
                transfer.getAmount());

        transfer.setStatus(ScheduledTransferStatus.COMPLETED);
        transfer.setExecutedAt(LocalDateTime.now(clock));
        scheduledTransferRepository.save(transfer);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long scheduledTransferId, String reason) {
        scheduledTransferRepository.findById(scheduledTransferId).ifPresent(transfer -> {
            if (transfer.getStatus() != ScheduledTransferStatus.PENDING) {
                return;
            }
            transfer.setStatus(ScheduledTransferStatus.FAILED);
            transfer.setFailureReason(reason);
            transfer.setExecutedAt(LocalDateTime.now(clock));
            scheduledTransferRepository.save(transfer);
        });
    }

}
