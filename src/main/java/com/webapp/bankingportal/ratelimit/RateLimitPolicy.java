package com.webapp.bankingportal.ratelimit;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum RateLimitPolicy {
    LOGIN(RateLimitProperties::getLogin,
            Set.of("/api/users/login", "/api/users/verify-otp")),
    OTP(RateLimitProperties::getOtp,
            Set.of("/api/users/generate-otp", "/api/auth/password-reset/send-otp")),
    PASSWORD_RESET(RateLimitProperties::getPasswordReset,
            Set.of("/api/auth/password-reset/verify-otp", "/api/auth/password-reset"));

    private final Function<RateLimitProperties, RateLimitProperties.Policy> settings;
    private final Set<String> paths;

    public RateLimitProperties.Policy settings(RateLimitProperties properties) {
        return settings.apply(properties);
    }

    public static Optional<RateLimitPolicy> forPath(String path) {
        return Arrays.stream(values())
                .filter(policy -> policy.paths.contains(path))
                .findFirst();
    }
}
