package com.webapp.bankingportal.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import com.webapp.bankingportal.entity.BalanceAuditLog;

/**
 * Append-only repository: deliberately exposes no update or delete operations.
 */
@org.springframework.stereotype.Repository
public interface BalanceAuditLogRepository extends Repository<BalanceAuditLog, Long> {

    BalanceAuditLog save(BalanceAuditLog entry);

    Optional<BalanceAuditLog> findById(Long id);

    List<BalanceAuditLog> findByAccountNumberOrderByIdAsc(String accountNumber);

    Page<BalanceAuditLog> findByAccountNumberAndCreatedAtBetween(
            String accountNumber, Instant from, Instant to, Pageable pageable);

}
