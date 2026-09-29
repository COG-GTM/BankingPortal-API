package com.webapp.bankingportal.fee;

public class FlatTransferFeeStrategy implements TransferFeeStrategy {

    private final double fee;

    public FlatTransferFeeStrategy(double fee) {
        if (fee < 0) {
            throw new IllegalArgumentException("Flat transfer fee must not be negative");
        }
        this.fee = TransferFeeStrategy.roundToCents(fee);
    }

    @Override
    public double calculateFee(String sourceAccountNumber, double amount) {
        return fee;
    }

}
