package com.webapp.bankingportal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.webapp.bankingportal.service.TransferPolicy;

public class TransferPolicyTests {

    @Test
    public void test_fee_is_zero_by_default() {
        var policy = new TransferPolicy(0, 0, 0.8, 0);
        Assertions.assertEquals(0.0, policy.calculateFee(1000));
    }

    @Test
    public void test_fee_combines_flat_and_percentage_rounded_to_cents() {
        var policy = new TransferPolicy(2.5, 0.333, 0.8, 0);
        // 1000 * 0.333% = 3.33, + 2.5 flat = 5.83
        Assertions.assertEquals(5.83, policy.calculateFee(1000));
        // 100 * 0.333% = 0.333 -> 0.33, + 2.5 = 2.83
        Assertions.assertEquals(2.83, policy.calculateFee(100));
    }

    @Test
    public void test_fee_is_zero_for_non_positive_amount() {
        var policy = new TransferPolicy(5, 1, 0.8, 0);
        Assertions.assertEquals(0.0, policy.calculateFee(0));
        Assertions.assertEquals(0.0, policy.calculateFee(-100));
    }

    @Test
    public void test_approaching_limit_boundaries() {
        var policy = new TransferPolicy(0, 0, 0.8, 0);
        Assertions.assertFalse(policy.isApproachingTransactionLimit(79_900));
        Assertions.assertTrue(policy.isApproachingTransactionLimit(80_000));
        Assertions.assertTrue(policy.isApproachingTransactionLimit(100_000));
        Assertions.assertFalse(policy.isApproachingTransactionLimit(100_100));
    }

    @Test
    public void test_negative_configuration_is_rejected() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new TransferPolicy(-1, 0, 0.8, 0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new TransferPolicy(0, -1, 0.8, 0));
    }
}
