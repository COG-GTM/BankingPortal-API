package com.webapp.bankingportal.fee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import lombok.val;

class PercentageTransferFeeStrategyTests {

    @Test
    void chargesPercentageOfAmount() {
        val strategy = new PercentageTransferFeeStrategy(1.5, 0.0, null);

        assertEquals(15.0, strategy.calculateFee("ACC001", 1000.0));
        assertEquals(1500.0, strategy.calculateFee("ACC001", 100000.0));
    }

    @Test
    void appliesMinimumFee() {
        val strategy = new PercentageTransferFeeStrategy(1.0, 5.0, null);

        assertEquals(5.0, strategy.calculateFee("ACC001", 100.0));
        assertEquals(10.0, strategy.calculateFee("ACC001", 1000.0));
    }

    @Test
    void appliesMaximumFee() {
        val strategy = new PercentageTransferFeeStrategy(2.0, 0.0, 50.0);

        assertEquals(20.0, strategy.calculateFee("ACC001", 1000.0));
        assertEquals(50.0, strategy.calculateFee("ACC001", 10000.0));
    }

    @Test
    void roundsFeeToCents() {
        val strategy = new PercentageTransferFeeStrategy(0.333, 0.0, null);

        assertEquals(0.33, strategy.calculateFee("ACC001", 100.0));
        assertEquals(1.67, strategy.calculateFee("ACC001", 500.0));
    }

    @Test
    void zeroRateChargesNothing() {
        assertEquals(0.0, new PercentageTransferFeeStrategy(0.0, 0.0, null).calculateFee("ACC001", 1000.0));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new PercentageTransferFeeStrategy(-1.0, 0.0, null));
        assertThrows(IllegalArgumentException.class, () -> new PercentageTransferFeeStrategy(1.0, -1.0, null));
        assertThrows(IllegalArgumentException.class, () -> new PercentageTransferFeeStrategy(1.0, 10.0, 5.0));
    }

}
