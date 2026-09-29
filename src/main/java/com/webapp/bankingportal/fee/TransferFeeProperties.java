package com.webapp.bankingportal.fee;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "transfer.fee")
public class TransferFeeProperties {

    public enum StrategyType {
        FLAT, PERCENTAGE, FREE_TIER
    }

    private StrategyType strategy = StrategyType.FLAT;
    private Flat flat = new Flat();
    private Percentage percentage = new Percentage();
    private FreeTier freeTier = new FreeTier();

    @Data
    public static class Flat {
        private double amount = 0.0;
    }

    @Data
    public static class Percentage {
        private double rate = 0.0;
        private double min = 0.0;
        private Double max;
    }

    @Data
    public static class FreeTier {
        private int freeTransfersPerMonth = 5;
        private StrategyType paidStrategy = StrategyType.FLAT;
    }

}
