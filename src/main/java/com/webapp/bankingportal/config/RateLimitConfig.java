package com.webapp.bankingportal.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.webapp.bankingportal.ratelimit.RateLimitFilter;
import com.webapp.bankingportal.ratelimit.RateLimitProperties;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitProperties properties) {
        final FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(
                new RateLimitFilter(properties, Clock.systemUTC()));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/api/users/*", "/api/auth/*");
        return registration;
    }

}
