package com.webapp.bankingportal.fee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import lombok.val;

class FlatTransferFeeStrategyTests {

    @Test
    void chargesSameFeeRegardlessOfAmount() {
        val strategy = new FlatTransferFeeStrategy(25.0);

        assertEquals(25.0, strategy.calculateFee("ACC001", 100.0));
        assertEquals(25.0, strategy.calculateFee("ACC001", 100000.0));
    }

    @Test
    void zeroFeeChargesNothing() {
        assertEquals(0.0, new FlatTransferFeeStrategy(0.0).calculateFee("ACC001", 500.0));
    }

    @Test
    void roundsFeeToCents() {
        assertEquals(1.24, new FlatTransferFeeStrategy(1.235).calculateFee("ACC001", 100.0));
    }

    @Test
    void rejectsNegativeFee() {
        assertThrows(IllegalArgumentException.class, () -> new FlatTransferFeeStrategy(-1.0));
    }

}
