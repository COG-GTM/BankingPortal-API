package com.webapp.bankingportal.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webapp.bankingportal.dto.ScheduledTransferRequest;
import com.webapp.bankingportal.entity.ScheduledTransfer;
import com.webapp.bankingportal.entity.ScheduledTransferStatus;
import com.webapp.bankingportal.exception.FundTransferException;
import com.webapp.bankingportal.exception.InsufficientBalanceException;
import com.webapp.bankingportal.exception.InvalidAmountException;
import com.webapp.bankingportal.exception.NotFoundException;
import com.webapp.bankingportal.exception.ScheduledTransferException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.ScheduledTransferRepository;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduledTransferServiceImpl implements ScheduledTransferService {

    private final ScheduledTransferRepository scheduledTransferRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final ScheduledTransferExecutor scheduledTransferExecutor;
    private final Clock clock;

    @Transactional
    @Override
    public ScheduledTransfer scheduleTransfer(String sourceAccountNumber, ScheduledTransferRequest request) {
        accountService.verifyPin(sourceAccountNumber, request.pin());
        accountService.validateAmount(request.amount());

        if (request.scheduledAt() == null) {
            throw new ScheduledTransferException(ApiMessages.SCHEDULED_TRANSFER_DATE_MISSING_ERROR.getMessage());
        }

        val now = LocalDateTime.now(clock);
        if (!request.scheduledAt().isAfter(now)) {
            throw new ScheduledTransferException(ApiMessages.SCHEDULED_TRANSFER_DATE_IN_PAST_ERROR.getMessage());
        }

        if (sourceAccountNumber.equals(request.targetAccountNumber())) {
            throw new FundTransferException(ApiMessages.CASH_TRANSFER_SAME_ACCOUNT_ERROR.getMessage());
        }

        val targetAccount = accountRepository.findByAccountNumber(request.targetAccountNumber());
        if (targetAccount == null) {
            throw new NotFoundException(ApiMessages.ACCOUNT_NOT_FOUND.getMessage());
        }

        val transfer = new ScheduledTransfer();
        transfer.setSourceAccount(accountRepository.findByAccountNumber(sourceAccountNumber));
        transfer.setTargetAccount(targetAccount);
        transfer.setAmount(request.amount());
        transfer.setScheduledAt(request.scheduledAt());
        transfer.setStatus(ScheduledTransferStatus.PENDING);
        transfer.setCreatedAt(now);
        return scheduledTransferRepository.save(transfer);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ScheduledTransfer> getScheduledTransfers(String sourceAccountNumber) {
        return scheduledTransferRepository.findBySourceAccount_AccountNumberOrderByScheduledAtDesc(sourceAccountNumber);
    }

    @Transactional
    @Override
    public void cancelScheduledTransfer(String sourceAccountNumber, Long scheduledTransferId) {
        val transfer = scheduledTransferRepository
                .findByIdAndSourceAccount_AccountNumber(scheduledTransferId, sourceAccountNumber)
                .orElseThrow(() -> new NotFoundException(ApiMessages.SCHEDULED_TRANSFER_NOT_FOUND.getMessage()));

        if (transfer.getStatus() != ScheduledTransferStatus.PENDING) {
            throw new ScheduledTransferException(ApiMessages.SCHEDULED_TRANSFER_NOT_PENDING_ERROR.getMessage());
        }

        transfer.setStatus(ScheduledTransferStatus.CANCELLED);
        scheduledTransferRepository.save(transfer);
    }

    @Override
    public int executeDueTransfers(LocalDateTime now) {
        val dueIds = scheduledTransferRepository.findIdsByStatusDueBefore(ScheduledTransferStatus.PENDING, now);
        var completed = 0;

        for (val id : dueIds) {
            try {
                if (scheduledTransferExecutor.execute(id)) {
                    completed++;
                }
            } catch (InsufficientBalanceException | InvalidAmountException | FundTransferException
                    | NotFoundException e) {
                log.warn("Scheduled transfer {} failed: {}", id, e.getMessage());
                scheduledTransferExecutor.markFailed(id, e.getMessage());
            } catch (RuntimeException e) {
                log.error("Scheduled transfer {} errored and will be retried: {}", id, e.getMessage(), e);
            }
        }

        return completed;
    }

}
