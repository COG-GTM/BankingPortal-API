package com.webapp.bankingportal.fee;

public class PercentageTransferFeeStrategy implements TransferFeeStrategy {

    private final double ratePercent;
    private final double minFee;
    private final Double maxFee;

    /**
     * @param ratePercent fee as a percentage of the amount (e.g. 1.5 means 1.5%)
     * @param minFee      lower bound applied to the computed fee
     * @param maxFee      upper bound applied to the computed fee, or null for no cap
     */
    public PercentageTransferFeeStrategy(double ratePercent, double minFee, Double maxFee) {
        if (ratePercent < 0) {
            throw new IllegalArgumentException("Transfer fee percentage must not be negative");
        }
        if (minFee < 0) {
            throw new IllegalArgumentException("Minimum transfer fee must not be negative");
        }
        if (maxFee != null && maxFee < minFee) {
            throw new IllegalArgumentException("Maximum transfer fee must not be less than the minimum");
        }
        this.ratePercent = ratePercent;
        this.minFee = minFee;
        this.maxFee = maxFee;
    }

    @Override
    public double calculateFee(String sourceAccountNumber, double amount) {
        var fee = Math.max(amount * ratePercent / 100.0, minFee);
        if (maxFee != null) {
            fee = Math.min(fee, maxFee);
        }
        return TransferFeeStrategy.roundToCents(fee);
    }

}
