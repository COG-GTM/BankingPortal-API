package com.webapp.bankingportal.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import com.webapp.bankingportal.config.RateLimitConfig;

class RateLimitConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RateLimitConfig.class);

    @Test
    void usesDefaultsWhenNotConfigured() {
        contextRunner.run(context -> {
            final RateLimitProperties properties = context.getBean(RateLimitProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.isTrustForwardedHeaders()).isFalse();
            assertThat(properties.getLogin().getMaxRequests()).isEqualTo(10);
            assertThat(properties.getLogin().getWindow()).isEqualTo(Duration.ofMinutes(1));
            assertThat(properties.getOtp().getMaxRequests()).isEqualTo(5);
            assertThat(properties.getOtp().getWindow()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.getPasswordReset().getMaxRequests()).isEqualTo(5);
            assertThat(properties.getPasswordReset().getWindow()).isEqualTo(Duration.ofMinutes(15));
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
        });
    }

    @Test
    void bindsConfiguredLimits() {
        contextRunner
                .withPropertyValues(
                        "rate-limit.enabled=false",
                        "rate-limit.trust-forwarded-headers=true",
                        "rate-limit.login.max-requests=20",
                        "rate-limit.login.window=30s",
                        "rate-limit.otp.max-requests=1",
                        "rate-limit.otp.window=2h",
                        "rate-limit.password-reset.max-requests=7",
                        "rate-limit.password-reset.window=10m")
                .run(context -> {
                    final RateLimitProperties properties = context.getBean(RateLimitProperties.class);
                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(properties.isTrustForwardedHeaders()).isTrue();
                    assertThat(properties.getLogin().getMaxRequests()).isEqualTo(20);
                    assertThat(properties.getLogin().getWindow()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(properties.getOtp().getMaxRequests()).isEqualTo(1);
                    assertThat(properties.getOtp().getWindow()).isEqualTo(Duration.ofHours(2));
                    assertThat(properties.getPasswordReset().getMaxRequests()).isEqualTo(7);
                    assertThat(properties.getPasswordReset().getWindow()).isEqualTo(Duration.ofMinutes(10));
                });
    }
}
