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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.Data;

@Entity
@Data
@Table(indexes = @Index(name = "idx_recurring_transfer_due", columnList = "status, nextExecutionDate"))
public class RecurringTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "source_account_id")
    private Account sourceAccount;

    @ManyToOne(optional = false)
    @JoinColumn(name = "target_account_id")
    private Account targetAccount;

    private double amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurrenceFrequency frequency;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    /** Null once the schedule is cancelled or completed. */
    private LocalDate nextExecutionDate;

    /** Number of schedule slots consumed (executed, failed or skipped). */
    private int occurrencesProcessed;

    private LocalDate lastExecutionDate;

    private String lastFailureReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurringTransferStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime cancelledAt;

    @Version
    private Long version;

}
