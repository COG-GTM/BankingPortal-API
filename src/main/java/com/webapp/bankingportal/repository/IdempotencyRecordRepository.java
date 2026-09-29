package com.webapp.bankingportal.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.webapp.bankingportal.entity.IdempotencyRecord;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByAccountNumberAndIdempotencyKey(String accountNumber, String idempotencyKey);

    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
