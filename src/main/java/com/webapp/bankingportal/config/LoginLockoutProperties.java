package com.webapp.bankingportal.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Operational knobs for password-login lockout. See README "Account lockout
 * and login backoff" for semantics.
 */
@ConfigurationProperties(prefix = "security.login-lockout")
public record LoginLockoutProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("1m") Duration baseLockDuration,
        @DefaultValue("2.0") double backoffMultiplier,
        @DefaultValue("1h") Duration maxLockDuration,
        @DefaultValue("24h") Duration failureResetWindow) {

    public LoginLockoutProperties {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("security.login-lockout.max-attempts must be >= 1");
        }
        if (backoffMultiplier < 1.0) {
            throw new IllegalArgumentException("security.login-lockout.backoff-multiplier must be >= 1.0");
        }
        requirePositive(baseLockDuration, "base-lock-duration");
        requirePositive(maxLockDuration, "max-lock-duration");
        requirePositive(failureResetWindow, "failure-reset-window");
        if (maxLockDuration.compareTo(baseLockDuration) < 0) {
            throw new IllegalArgumentException(
                    "security.login-lockout.max-lock-duration must be >= base-lock-duration");
        }
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("security.login-lockout." + name + " must be positive");
        }
    }
}
