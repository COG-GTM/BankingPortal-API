package com.webapp.bankingportal.entity;

import java.math.BigDecimal;
import java.util.Date;

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
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Amount debited from the source account, in {@link #sourceCurrency}. */
    private double amount;

    @Column(length = 3)
    private String sourceCurrency;

    /** Amount credited to the target account, in {@link #targetCurrency}. Null for non-transfers. */
    private Double targetAmount;

    @Column(length = 3)
    private String targetCurrency;

    /** Units of target currency per unit of source currency applied to this transaction. */
    @Column(precision = 19, scale = 8)
    private BigDecimal exchangeRate;

    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    private Date transactionDate;

    @ManyToOne
    @JoinColumn(name = "source_account_id")
    private Account sourceAccount;

    @ManyToOne
    @JoinColumn(name = "target_account_id")
    private Account targetAccount;

}
