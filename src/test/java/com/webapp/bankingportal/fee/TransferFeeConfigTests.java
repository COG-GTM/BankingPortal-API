package com.webapp.bankingportal.fee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.webapp.bankingportal.fee.TransferFeeProperties.StrategyType;
import com.webapp.bankingportal.repository.TransactionRepository;

import lombok.val;

class TransferFeeConfigTests {

    private final TransferFeeConfig config = new TransferFeeConfig();
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);

    @Test
    void defaultsToZeroFlatFee() {
        val strategy = config.transferFeeStrategy(new TransferFeeProperties(), transactionRepository);

        assertInstanceOf(FlatTransferFeeStrategy.class, strategy);
        assertEquals(0.0, strategy.calculateFee("ACC001", 1000.0));
    }

    @Test
    void buildsFlatStrategy() {
        val properties = new TransferFeeProperties();
        properties.getFlat().setAmount(7.5);

        assertEquals(7.5, config.transferFeeStrategy(properties, transactionRepository).calculateFee("ACC001", 100.0));
    }

    @Test
    void buildsPercentageStrategy() {
        val properties = new TransferFeeProperties();
        properties.setStrategy(StrategyType.PERCENTAGE);
        properties.getPercentage().setRate(2.0);
        properties.getPercentage().setMax(30.0);

        val strategy = config.transferFeeStrategy(properties, transactionRepository);

        assertInstanceOf(PercentageTransferFeeStrategy.class, strategy);
        assertEquals(20.0, strategy.calculateFee("ACC001", 1000.0));
        assertEquals(30.0, strategy.calculateFee("ACC001", 5000.0));
    }

    @Test
    void buildsFreeTierStrategy() {
        val properties = new TransferFeeProperties();
        properties.setStrategy(StrategyType.FREE_TIER);

        assertInstanceOf(FreeTierTransferFeeStrategy.class, config.transferFeeStrategy(properties, transactionRepository));
    }

    @Test
    void rejectsFreeTierAsPaidStrategy() {
        val properties = new TransferFeeProperties();
        properties.setStrategy(StrategyType.FREE_TIER);
        properties.getFreeTier().setPaidStrategy(StrategyType.FREE_TIER);

        assertThrows(IllegalArgumentException.class, () -> config.transferFeeStrategy(properties, transactionRepository));
    }

}
