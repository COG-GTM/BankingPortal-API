package com.webapp.bankingportal.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import lombok.Data;

@Entity
@Data
public class RecurringTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "source_account_id", nullable = false)
    private Account sourceAccount;

    @ManyToOne
    @JoinColumn(name = "target_account_id", nullable = false)
    private Account targetAccount;

    private double amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferFrequency frequency;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    /** Next scheduled run; null once the schedule is no longer active. */
    private LocalDate nextExecutionDate;

    /** Number of occurrences (successful or failed) processed so far. */
    private int occurrencesProcessed;

    private int failedOccurrences;

    private String lastFailureReason;

    private LocalDateTime lastExecutedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurringTransferStatus status;

    private LocalDateTime createdAt;

}
