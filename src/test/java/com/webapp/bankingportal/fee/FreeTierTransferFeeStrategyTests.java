package com.webapp.bankingportal.fee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.repository.TransactionRepository;

import lombok.val;

@ExtendWith(MockitoExtension.class)
class FreeTierTransferFeeStrategyTests {

    private static final String ACCOUNT = "ACC001";

    @Mock
    private TransactionRepository transactionRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-17T10:15:30Z"), ZoneOffset.UTC);

    private FreeTierTransferFeeStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new FreeTierTransferFeeStrategy(3, new FlatTransferFeeStrategy(10.0), transactionRepository, clock);
    }

    private void givenTransfersThisMonth(long count) {
        when(transactionRepository.countBySourceAccount_AccountNumberAndTransactionTypeAndTransactionDateGreaterThanEqual(
                eq(ACCOUNT), eq(TransactionType.CASH_TRANSFER), any(Date.class))).thenReturn(count);
    }

    @Test
    void transfersWithinFreeTierAreFree() {
        givenTransfersThisMonth(0);
        assertEquals(0.0, strategy.calculateFee(ACCOUNT, 1000.0));

        givenTransfersThisMonth(2);
        assertEquals(0.0, strategy.calculateFee(ACCOUNT, 1000.0));
    }

    @Test
    void transfersBeyondFreeTierUsePaidStrategy() {
        givenTransfersThisMonth(3);
        assertEquals(10.0, strategy.calculateFee(ACCOUNT, 1000.0));

        givenTransfersThisMonth(10);
        assertEquals(10.0, strategy.calculateFee(ACCOUNT, 1000.0));
    }

    @Test
    void delegatesToPercentagePaidStrategy() {
        val percentageStrategy = new FreeTierTransferFeeStrategy(1,
                new PercentageTransferFeeStrategy(1.0, 0.0, null), transactionRepository, clock);
        givenTransfersThisMonth(1);

        assertEquals(20.0, percentageStrategy.calculateFee(ACCOUNT, 2000.0));
    }

    @Test
    void countsTransfersFromStartOfCurrentMonth() {
        givenTransfersThisMonth(0);

        strategy.calculateFee(ACCOUNT, 1000.0);

        verify(transactionRepository).countBySourceAccount_AccountNumberAndTransactionTypeAndTransactionDateGreaterThanEqual(
                ACCOUNT, TransactionType.CASH_TRANSFER, Date.from(Instant.parse("2026-09-01T00:00:00Z")));
    }

    @Test
    void zeroFreeTransfersAlwaysCharges() {
        val noFreeTier = new FreeTierTransferFeeStrategy(0, new FlatTransferFeeStrategy(10.0),
                transactionRepository, clock);
        givenTransfersThisMonth(0);

        assertEquals(10.0, noFreeTier.calculateFee(ACCOUNT, 1000.0));
    }

    @Test
    void rejectsNegativeFreeTransfers() {
        assertThrows(IllegalArgumentException.class,
                () -> new FreeTierTransferFeeStrategy(-1, new FlatTransferFeeStrategy(0.0), transactionRepository, clock));
    }

}
