package com.webapp.bankingportal.fee;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Date;

import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.repository.TransactionRepository;

import lombok.val;

/**
 * Waives the fee for the first {@code freeTransfersPerMonth} transfers an
 * account makes in the current calendar month, then charges according to the
 * wrapped {@code paidStrategy}.
 */
public class FreeTierTransferFeeStrategy implements TransferFeeStrategy {

    private final int freeTransfersPerMonth;
    private final TransferFeeStrategy paidStrategy;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    public FreeTierTransferFeeStrategy(int freeTransfersPerMonth, TransferFeeStrategy paidStrategy,
            TransactionRepository transactionRepository, Clock clock) {
        if (freeTransfersPerMonth < 0) {
            throw new IllegalArgumentException("Free transfers per month must not be negative");
        }
        this.freeTransfersPerMonth = freeTransfersPerMonth;
        this.paidStrategy = paidStrategy;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    @Override
    public double calculateFee(String sourceAccountNumber, double amount) {
        val startOfMonth = LocalDate.now(clock).withDayOfMonth(1).atStartOfDay(clock.getZone()).toInstant();
        val transfersThisMonth = transactionRepository
                .countBySourceAccount_AccountNumberAndTransactionTypeAndTransactionDateGreaterThanEqual(
                        sourceAccountNumber, TransactionType.CASH_TRANSFER, Date.from(startOfMonth));

        if (transfersThisMonth < freeTransfersPerMonth) {
            return 0.0;
        }
        return paidStrategy.calculateFee(sourceAccountNumber, amount);
    }

}
