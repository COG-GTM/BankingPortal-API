package com.webapp.bankingportal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import com.webapp.bankingportal.service.EmailService;
import com.webapp.bankingportal.service.EmailServiceImpl;

@Configuration
public class TestConfig {

    @Bean
    @Primary
    public EmailService emailService() {
        return new EmailServiceImpl(new GreenMailJavaMailSender());
    }

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory("localhost", 6379);
    }
}
