package com.webapp.bankingportal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "velocity")
public class VelocityCheckProperties {

    private boolean enabled = true;

    /** A single transfer strictly above this amount is flagged. */
    private double perTransactionAmountThreshold = 50_000;

    /** Flag when the day's transfer total (including the current one) is strictly above this amount. */
    private double dailyAmountThreshold = 200_000;

    /** Flag when the day's transfer count (including the current one) is strictly above this number. */
    private int dailyCountThreshold = 5;

    /** Time zone used to determine calendar-day boundaries for the daily checks. */
    private String zoneId = "UTC";

}
