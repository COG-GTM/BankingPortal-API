package com.webapp.bankingportal.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@Table(name = "idempotency_record",
        uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_account_key",
                columnNames = { "account_number", "idempotency_key" }),
        indexes = @Index(name = "idx_idempotency_expires_at", columnList = "expires_at"))
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    private Integer responseStatus;

    @Column(length = 4000)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public IdempotencyRecord(String accountNumber, String idempotencyKey, String requestHash,
            Instant createdAt, Instant expiresAt) {
        this.accountNumber = accountNumber;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

}
