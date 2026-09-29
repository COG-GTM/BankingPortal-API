package com.webapp.bankingportal.fee;

import java.math.BigDecimal;
import java.math.RoundingMode;

public interface TransferFeeStrategy {

    double calculateFee(String sourceAccountNumber, double amount);

    static double roundToCents(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

}
