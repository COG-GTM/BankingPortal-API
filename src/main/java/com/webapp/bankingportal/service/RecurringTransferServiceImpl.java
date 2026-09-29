package com.webapp.bankingportal.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.webapp.bankingportal.dto.RecurringTransferRequest;
import com.webapp.bankingportal.entity.RecurringTransfer;
import com.webapp.bankingportal.entity.RecurringTransferStatus;
import com.webapp.bankingportal.exception.FundTransferException;
import com.webapp.bankingportal.exception.NotFoundException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.RecurringTransferRepository;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecurringTransferServiceImpl implements RecurringTransferService {

    private final RecurringTransferRepository recurringTransferRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final Clock clock;

    @Override
    public RecurringTransfer createRecurringTransfer(String sourceAccountNumber, RecurringTransferRequest request) {
        validateSchedule(request);
        accountService.validateFundTransfer(sourceAccountNumber, request.targetAccountNumber(), request.pin(),
                request.amount());

        val transfer = new RecurringTransfer();
        transfer.setSourceAccount(accountRepository.findByAccountNumber(sourceAccountNumber));
        transfer.setTargetAccount(accountRepository.findByAccountNumber(request.targetAccountNumber()));
        transfer.setAmount(request.amount());
        transfer.setFrequency(request.frequency());
        transfer.setStartDate(request.startDate());
        transfer.setEndDate(request.endDate());
        transfer.setNextExecutionDate(request.startDate());
        transfer.setStatus(RecurringTransferStatus.ACTIVE);
        transfer.setCreatedAt(LocalDateTime.now(clock));

        log.info("Created {} recurring transfer from {} to {} ({} - {})", request.frequency(),
                sourceAccountNumber, request.targetAccountNumber(), request.startDate(), request.endDate());
        return recurringTransferRepository.save(transfer);
    }

    private void validateSchedule(RecurringTransferRequest request) {
        if (request.frequency() == null) {
            throw new FundTransferException(ApiMessages.RECURRING_TRANSFER_FREQUENCY_MISSING.getMessage());
        }
        if (request.startDate() == null || request.endDate() == null) {
            throw new FundTransferException(ApiMessages.RECURRING_TRANSFER_DATES_MISSING.getMessage());
        }
        if (request.startDate().isBefore(LocalDate.now(clock))) {
            throw new FundTransferException(ApiMessages.RECURRING_TRANSFER_START_IN_PAST.getMessage());
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new FundTransferException(ApiMessages.RECURRING_TRANSFER_END_BEFORE_START.getMessage());
        }
    }

    @Override
    public List<RecurringTransfer> getRecurringTransfers(String sourceAccountNumber) {
        return recurringTransferRepository.findBySourceAccount_AccountNumberOrderByIdAsc(sourceAccountNumber);
    }

    @Override
    public RecurringTransfer cancelRecurringTransfer(String sourceAccountNumber, Long recurringTransferId) {
        val transfer = recurringTransferRepository.findById(recurringTransferId)
                .filter(t -> t.getSourceAccount().getAccountNumber().equals(sourceAccountNumber))
                .orElseThrow(() -> new NotFoundException(ApiMessages.RECURRING_TRANSFER_NOT_FOUND.getMessage()));

        if (transfer.getStatus() != RecurringTransferStatus.ACTIVE) {
            throw new FundTransferException(ApiMessages.RECURRING_TRANSFER_NOT_ACTIVE.getMessage());
        }

        transfer.setStatus(RecurringTransferStatus.CANCELLED);
        transfer.setNextExecutionDate(null);
        return recurringTransferRepository.save(transfer);
    }

    /**
     * Not transactional on purpose: each occurrence commits (or rolls back) in
     * its own transaction inside {@link AccountService}, so one failed
     * transfer does not affect the others.
     */
    @Override
    public int executeDueTransfers(LocalDate today) {
        val due = recurringTransferRepository
                .findByStatusAndNextExecutionDateLessThanEqual(RecurringTransferStatus.ACTIVE, today);
        for (val transfer : due) {
            executeOccurrence(transfer, today);
        }
        return due.size();
    }

    private void executeOccurrence(RecurringTransfer transfer, LocalDate today) {
        val source = transfer.getSourceAccount().getAccountNumber();
        val target = transfer.getTargetAccount().getAccountNumber();
        try {
            accountService.executeScheduledFundTransfer(source, target, transfer.getAmount());
            transfer.setLastFailureReason(null);
            log.info("Recurring transfer {} executed: {} -> {} amount {}", transfer.getId(), source, target,
                    transfer.getAmount());
        } catch (RuntimeException e) {
            transfer.setFailedOccurrences(transfer.getFailedOccurrences() + 1);
            transfer.setLastFailureReason(e.getMessage());
            log.warn("Recurring transfer {} failed: {}", transfer.getId(), e.getMessage());
        }

        transfer.setOccurrencesProcessed(transfer.getOccurrencesProcessed() + 1);
        transfer.setLastExecutedAt(LocalDateTime.now(clock));

        val next = RecurringTransferSchedule.nextOccurrenceAfter(transfer.getStartDate(), transfer.getEndDate(),
                transfer.getFrequency(), today);
        transfer.setNextExecutionDate(next.orElse(null));
        if (next.isEmpty()) {
            transfer.setStatus(RecurringTransferStatus.COMPLETED);
        }
        recurringTransferRepository.save(transfer);
    }
}
