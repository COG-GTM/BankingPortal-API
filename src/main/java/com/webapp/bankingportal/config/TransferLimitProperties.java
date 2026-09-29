package com.webapp.bankingportal.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Validated
@ConfigurationProperties(prefix = "banking.transfer")
public class TransferLimitProperties {

    /**
     * Default maximum total amount an account may transfer out per calendar day.
     * Can be overridden per account via {@code Account#dailyTransferLimit}.
     */
    @Positive
    private double dailyLimit = 200_000;

    /**
     * Time zone used to determine calendar-day boundaries for the daily limit.
     */
    @NotNull
    private ZoneId zone = ZoneId.of("UTC");

}
