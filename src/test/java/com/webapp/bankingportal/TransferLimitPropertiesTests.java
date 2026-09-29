package com.webapp.bankingportal;

import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.webapp.bankingportal.config.TransferLimitConfig;
import com.webapp.bankingportal.config.TransferLimitProperties;

import lombok.val;

public class TransferLimitPropertiesTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(TransferLimitConfig.class);

    @Test
    public void test_defaults_apply_when_not_configured() {
        contextRunner.run(context -> {
            val properties = context.getBean(TransferLimitProperties.class);
            Assertions.assertEquals(200_000.0, properties.getDailyLimit());
            Assertions.assertEquals(ZoneId.of("UTC"), properties.getZone());
            Assertions.assertNotNull(context.getBean(Clock.class));
        });
    }

    @Test
    public void test_limit_and_zone_bind_from_configuration() {
        contextRunner
                .withPropertyValues("banking.transfer.daily-limit=50000", "banking.transfer.zone=Asia/Kolkata")
                .run(context -> {
                    val properties = context.getBean(TransferLimitProperties.class);
                    Assertions.assertEquals(50_000.0, properties.getDailyLimit());
                    Assertions.assertEquals(ZoneId.of("Asia/Kolkata"), properties.getZone());
                });
    }

    @Test
    public void test_zero_limit_is_rejected() {
        contextRunner
                .withPropertyValues("banking.transfer.daily-limit=0")
                .run(context -> Assertions.assertNotNull(context.getStartupFailure()));
    }

    @Test
    public void test_negative_limit_is_rejected() {
        contextRunner
                .withPropertyValues("banking.transfer.daily-limit=-100")
                .run(context -> Assertions.assertNotNull(context.getStartupFailure()));
    }

}
