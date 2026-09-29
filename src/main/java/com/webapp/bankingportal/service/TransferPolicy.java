package com.webapp.bankingportal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

@Component
@Getter
public class TransferPolicy {

    public static final double MAX_TRANSACTION_AMOUNT = 100_000;
    public static final double AMOUNT_STEP = 100;

    private final double flatFee;
    private final double percentageFee;
    private final double limitWarningRatio;
    private final double lowBalanceThreshold;

    public TransferPolicy(
            @Value("${banking.transfer.fee.flat:0}") double flatFee,
            @Value("${banking.transfer.fee.percentage:0}") double percentageFee,
            @Value("${banking.transfer.warning.limit-ratio:0.8}") double limitWarningRatio,
            @Value("${banking.transfer.warning.low-balance-threshold:0}") double lowBalanceThreshold) {
        if (flatFee < 0 || percentageFee < 0 || limitWarningRatio < 0 || lowBalanceThreshold < 0) {
            throw new IllegalArgumentException("Transfer policy values must not be negative");
        }
        this.flatFee = flatFee;
        this.percentageFee = percentageFee;
        this.limitWarningRatio = limitWarningRatio;
        this.lowBalanceThreshold = lowBalanceThreshold;
    }

    public double calculateFee(double amount) {
        if (amount <= 0) {
            return 0;
        }

        return BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(percentageFee))
                .divide(BigDecimal.valueOf(100))
                .add(BigDecimal.valueOf(flatFee))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public boolean isApproachingTransactionLimit(double amount) {
        return amount >= MAX_TRANSACTION_AMOUNT * limitWarningRatio && amount <= MAX_TRANSACTION_AMOUNT;
    }

    public boolean isLowBalance(double balance) {
        return balance >= 0 && balance < lowBalanceThreshold;
    }
}
