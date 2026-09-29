package com.webapp.bankingportal.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StatementConfig {

    /**
     * Clock used for statement generation. Its zone defines calendar-month
     * boundaries and how transaction timestamps are displayed on statements.
     */
    @Bean
    public Clock statementClock(@Value("${statement.time-zone:UTC}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
