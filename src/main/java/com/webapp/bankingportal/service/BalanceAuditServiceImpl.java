package com.webapp.bankingportal.service;

import java.time.Instant;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.webapp.bankingportal.dto.BalanceAuditLogResponse;
import com.webapp.bankingportal.dto.BalanceAuditTrailResponse;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.BalanceAuditLog;
import com.webapp.bankingportal.entity.Transaction;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.repository.BalanceAuditLogRepository;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.RequiredArgsConstructor;
import lombok.val;

@Service
@RequiredArgsConstructor
public class BalanceAuditServiceImpl implements BalanceAuditService {

    static final int MAX_PAGE_SIZE = 100;

    private final BalanceAuditLogRepository balanceAuditLogRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BalanceAuditLog record(Account account, TransactionType operation, double balanceBefore, Transaction transaction,
            String performedBy, String counterpartyAccountNumber) {
        val entry = BalanceAuditLog.builder()
                .accountNumber(account.getAccountNumber())
                .operation(operation)
                .amount(transaction.getAmount())
                .balanceBefore(balanceBefore)
                .balanceAfter(account.getBalance())
                .performedBy(performedBy)
                .counterpartyAccountNumber(counterpartyAccountNumber)
                .transactionId(transaction.getId())
                .build();
        return balanceAuditLogRepository.save(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceAuditTrailResponse getAuditTrail(String accountNumber, Instant from, Instant to,
            int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException(ApiMessages.AUDIT_PAGE_INVALID_ERROR.getMessage());
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    String.format(ApiMessages.AUDIT_PAGE_SIZE_INVALID_ERROR.getMessage(), MAX_PAGE_SIZE));
        }

        val effectiveFrom = from != null ? from : Instant.EPOCH;
        val effectiveTo = to != null ? to : Instant.now();
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new IllegalArgumentException(ApiMessages.AUDIT_DATE_RANGE_INVALID_ERROR.getMessage());
        }

        val pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        val result = balanceAuditLogRepository.findByAccountNumberAndCreatedAtBetween(
                accountNumber, effectiveFrom, effectiveTo, pageable);

        return new BalanceAuditTrailResponse(
                result.getContent().stream().map(BalanceAuditLogResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

}
