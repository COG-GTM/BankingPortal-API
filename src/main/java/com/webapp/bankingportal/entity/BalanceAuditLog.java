package com.webapp.bankingportal.entity;

import java.time.Instant;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;

/**
 * Append-only record of a single balance change on an account. Entries are
 * never updated or deleted once written.
 */
@Entity
@Immutable
@Table(name = "balance_audit_log", indexes = {
        @Index(name = "idx_balance_audit_account_time", columnList = "account_number, created_at")
})
@Getter
@ToString
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BalanceAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false, updatable = false)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TransactionType operation;

    @Column(nullable = false, updatable = false)
    private double amount;

    @Column(nullable = false, updatable = false)
    private double balanceBefore;

    @Column(nullable = false, updatable = false)
    private double balanceAfter;

    @Column(nullable = false, updatable = false)
    private String performedBy;

    @Column(updatable = false)
    private String counterpartyAccountNumber;

    @Column(updatable = false)
    private Long transactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        throw new UnsupportedOperationException("Balance audit log entries are immutable");
    }

    @PreRemove
    void onRemove() {
        throw new UnsupportedOperationException("Balance audit log entries cannot be deleted");
    }

}
