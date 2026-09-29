package com.webapp.bankingportal.fee;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.webapp.bankingportal.fee.TransferFeeProperties.StrategyType;
import com.webapp.bankingportal.repository.TransactionRepository;

@Configuration
@EnableConfigurationProperties(TransferFeeProperties.class)
public class TransferFeeConfig {

    @Bean
    public TransferFeeStrategy transferFeeStrategy(TransferFeeProperties properties,
            TransactionRepository transactionRepository) {
        return switch (properties.getStrategy()) {
            case FLAT, PERCENTAGE -> paidStrategy(properties.getStrategy(), properties);
            case FREE_TIER -> new FreeTierTransferFeeStrategy(
                    properties.getFreeTier().getFreeTransfersPerMonth(),
                    paidStrategy(properties.getFreeTier().getPaidStrategy(), properties),
                    transactionRepository,
                    Clock.systemDefaultZone());
        };
    }

    static TransferFeeStrategy paidStrategy(StrategyType type, TransferFeeProperties properties) {
        return switch (type) {
            case FLAT -> new FlatTransferFeeStrategy(properties.getFlat().getAmount());
            case PERCENTAGE -> new PercentageTransferFeeStrategy(
                    properties.getPercentage().getRate(),
                    properties.getPercentage().getMin(),
                    properties.getPercentage().getMax());
            case FREE_TIER -> throw new IllegalArgumentException(
                    "transfer.fee.free-tier.paid-strategy must be FLAT or PERCENTAGE");
        };
    }

}
