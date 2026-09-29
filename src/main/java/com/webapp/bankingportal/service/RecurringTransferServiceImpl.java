package com.webapp.bankingportal.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webapp.bankingportal.dto.DueRecurringTransfer;
import com.webapp.bankingportal.dto.RecurringTransferRequest;
import com.webapp.bankingportal.dto.RecurringTransferResponse;
import com.webapp.bankingportal.entity.RecurringTransfer;
import com.webapp.bankingportal.entity.RecurringTransferStatus;
import com.webapp.bankingportal.exception.NotFoundException;
import com.webapp.bankingportal.exception.RecurringTransferException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.RecurringTransferRepository;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.RecurringTransferSchedule;

import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecurringTransferServiceImpl implements RecurringTransferService {

    static final int MAX_SCHEDULE_YEARS = 5;
    static final int UPCOMING_PREVIEW_SIZE = 5;
    private static final int MAX_FAILURE_REASON_LENGTH = 255;

    private final RecurringTransferRepository recurringTransferRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final Clock clock;

    @Override
    @Transactional
    public RecurringTransferResponse createRecurringTransfer(String sourceAccountNumber,
            RecurringTransferRequest request) {
        validateSchedule(request);
        accountService.validateFundTransfer(sourceAccountNumber, request.targetAccountNumber(), request.pin(),
                request.amount());

        val recurringTransfer = new RecurringTransfer();
        recurringTransfer.setSourceAccount(accountRepository.findByAccountNumber(sourceAccountNumber));
        recurringTransfer.setTargetAccount(accountRepository.findByAccountNumber(request.targetAccountNumber()));
        recurringTransfer.setAmount(request.amount());
        recurringTransfer.setFrequency(request.frequency());
        recurringTransfer.setStartDate(request.startDate());
        recurringTransfer.setEndDate(request.endDate());
        recurringTransfer.setNextExecutionDate(request.startDate());
        recurringTransfer.setOccurrencesProcessed(0);
        recurringTransfer.setStatus(RecurringTransferStatus.ACTIVE);
        recurringTransfer.setCreatedAt(LocalDateTime.now(clock));

        val saved = recurringTransferRepository.save(recurringTransfer);
        log.info("Created {} recurring transfer {} from {} to {}", saved.getFrequency(), saved.getId(),
                sourceAccountNumber, request.targetAccountNumber());
        return toResponse(saved);
    }

    @Override
    public List<RecurringTransferResponse> getRecurringTransfers(String sourceAccountNumber) {
        return recurringTransferRepository.findBySourceAccount_AccountNumberOrderByIdDesc(sourceAccountNumber)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public RecurringTransferResponse getRecurringTransfer(String sourceAccountNumber, Long id) {
        return toResponse(findOwned(sourceAccountNumber, id));
    }

    @Override
    @Transactional
    public RecurringTransferResponse cancelRecurringTransfer(String sourceAccountNumber, Long id) {
        val recurringTransfer = findOwned(sourceAccountNumber, id);
        if (recurringTransfer.getStatus() != RecurringTransferStatus.ACTIVE) {
            throw new RecurringTransferException(String.format(
                    ApiMessages.RECURRING_TRANSFER_NOT_ACTIVE_ERROR.getMessage(),
                    recurringTransfer.getStatus()));
        }

        recurringTransfer.setStatus(RecurringTransferStatus.CANCELLED);
        recurringTransfer.setNextExecutionDate(null);
        recurringTransfer.setCancelledAt(LocalDateTime.now(clock));
        log.info("Cancelled recurring transfer {} for account {}", id, sourceAccountNumber);
        return toResponse(recurringTransferRepository.save(recurringTransfer));
    }

    @Override
    public List<DueRecurringTransfer> findDueTransfers(LocalDate date) {
        return recurringTransferRepository.findDueOnOrBefore(RecurringTransferStatus.ACTIVE, date);
    }

    @Override
    @Transactional
    public void executeOccurrence(Long id, LocalDate occurrenceDate, LocalDate date) {
        val recurringTransfer = findPendingOccurrence(id, occurrenceDate, date);
        if (recurringTransfer == null) {
            return;
        }

        accountService.scheduledFundTransfer(
                recurringTransfer.getSourceAccount().getAccountNumber(),
                recurringTransfer.getTargetAccount().getAccountNumber(),
                recurringTransfer.getAmount());

        recurringTransfer.setLastExecutionDate(date);
        recurringTransfer.setLastFailureReason(null);
        advancePast(recurringTransfer, date);
        recurringTransferRepository.save(recurringTransfer);
        log.info("Executed recurring transfer {} for occurrence {}", id, occurrenceDate);
    }

    @Override
    @Transactional
    public void recordFailedOccurrence(Long id, LocalDate occurrenceDate, LocalDate date, String reason) {
        val recurringTransfer = findPendingOccurrence(id, occurrenceDate, date);
        if (recurringTransfer == null) {
            return;
        }

        recurringTransfer.setLastFailureReason(truncate(reason));
        advancePast(recurringTransfer, date);
        recurringTransferRepository.save(recurringTransfer);
        log.warn("Recurring transfer {} failed for occurrence {}: {}", id, occurrenceDate, reason);
    }

    private RecurringTransfer findPendingOccurrence(Long id, LocalDate occurrenceDate, LocalDate date) {
        val recurringTransfer = recurringTransferRepository.findById(id).orElse(null);
        if (recurringTransfer == null
                || recurringTransfer.getStatus() != RecurringTransferStatus.ACTIVE
                || recurringTransfer.getNextExecutionDate() == null
                || !recurringTransfer.getNextExecutionDate().equals(occurrenceDate)
                || occurrenceDate.isAfter(date)) {
            log.debug("Recurring transfer {} has no pending occurrence on {}", id, occurrenceDate);
            return null;
        }
        return recurringTransfer;
    }

    private void advancePast(RecurringTransfer recurringTransfer, LocalDate date) {
        val nextIndex = RecurringTransferSchedule.firstIndexAfter(
                recurringTransfer.getStartDate(), recurringTransfer.getFrequency(), date);
        val nextDate = RecurringTransferSchedule.occurrenceOrNull(
                recurringTransfer.getStartDate(), recurringTransfer.getEndDate(),
                recurringTransfer.getFrequency(), nextIndex);

        recurringTransfer.setOccurrencesProcessed(nextIndex);
        recurringTransfer.setNextExecutionDate(nextDate);
        if (nextDate == null) {
            recurringTransfer.setStatus(RecurringTransferStatus.COMPLETED);
        }
    }

    private void validateSchedule(RecurringTransferRequest request) {
        if (request.frequency() == null) {
            throw new RecurringTransferException(ApiMessages.RECURRING_TRANSFER_FREQUENCY_MISSING_ERROR.getMessage());
        }

        if (request.startDate() == null || request.endDate() == null) {
            throw new RecurringTransferException(ApiMessages.RECURRING_TRANSFER_DATES_MISSING_ERROR.getMessage());
        }

        if (request.startDate().isBefore(LocalDate.now(clock))) {
            throw new RecurringTransferException(ApiMessages.RECURRING_TRANSFER_START_IN_PAST_ERROR.getMessage());
        }

        if (request.endDate().isBefore(request.startDate())) {
            throw new RecurringTransferException(ApiMessages.RECURRING_TRANSFER_END_BEFORE_START_ERROR.getMessage());
        }

        if (request.endDate().isAfter(request.startDate().plusYears(MAX_SCHEDULE_YEARS))) {
            throw new RecurringTransferException(String.format(
                    ApiMessages.RECURRING_TRANSFER_END_TOO_FAR_ERROR.getMessage(), MAX_SCHEDULE_YEARS));
        }
    }

    private RecurringTransfer findOwned(String sourceAccountNumber, Long id) {
        return recurringTransferRepository.findByIdAndSourceAccount_AccountNumber(id, sourceAccountNumber)
                .orElseThrow(() -> new NotFoundException(ApiMessages.RECURRING_TRANSFER_NOT_FOUND.getMessage()));
    }

    private RecurringTransferResponse toResponse(RecurringTransfer recurringTransfer) {
        val upcoming = recurringTransfer.getStatus() == RecurringTransferStatus.ACTIVE
                ? RecurringTransferSchedule.generateFrom(
                        recurringTransfer.getStartDate(),
                        recurringTransfer.getEndDate(),
                        recurringTransfer.getFrequency(),
                        recurringTransfer.getOccurrencesProcessed(),
                        UPCOMING_PREVIEW_SIZE)
                : List.<LocalDate>of();

        return new RecurringTransferResponse(
                recurringTransfer.getId(),
                recurringTransfer.getSourceAccount().getAccountNumber(),
                recurringTransfer.getTargetAccount().getAccountNumber(),
                recurringTransfer.getAmount(),
                recurringTransfer.getFrequency(),
                recurringTransfer.getStartDate(),
                recurringTransfer.getEndDate(),
                recurringTransfer.getStatus(),
                recurringTransfer.getNextExecutionDate(),
                recurringTransfer.getLastExecutionDate(),
                recurringTransfer.getLastFailureReason(),
                upcoming);
    }

    private static String truncate(String reason) {
        if (reason == null || reason.length() <= MAX_FAILURE_REASON_LENGTH) {
            return reason;
        }
        return reason.substring(0, MAX_FAILURE_REASON_LENGTH);
    }

}
