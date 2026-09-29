package com.webapp.bankingportal.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    /**
     * Use the first address in X-Forwarded-For as the client key. Only enable
     * behind a trusted reverse proxy, otherwise clients can spoof the header.
     */
    private boolean trustForwardedHeaders = false;

    private Policy login = new Policy(10, Duration.ofMinutes(1));

    private Policy otp = new Policy(5, Duration.ofMinutes(5));

    private Policy passwordReset = new Policy(5, Duration.ofMinutes(15));

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Policy {
        private int maxRequests;
        private Duration window;
    }

}
