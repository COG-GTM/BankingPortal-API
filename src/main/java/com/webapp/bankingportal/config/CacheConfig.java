package com.webapp.bankingportal.config;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.val;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        val cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(List.of("otpAttempts")); // Define the cache names
        cacheManager.setCaffeine(caffeineConfig());
        cacheManager.registerCustomCache("idempotency", idempotencyCaffeineConfig().build());
        return cacheManager;
    }

    public Caffeine<Object, Object> caffeineConfig() {
        return Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES) // Cache entries expire after 15 minutes
                .maximumSize(100) // Maximum of 100 entries in the cache
                .recordStats(); // For monitoring cache statistics (optional)
    }

    // Sized well above the expected request volume in the retry window, so
    // entries expire by time rather than being evicted early and replayed.
    public Caffeine<Object, Object> idempotencyCaffeineConfig() {
        return Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(100_000)
                .recordStats();
    }

}
