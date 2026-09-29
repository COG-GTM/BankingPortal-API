package com.webapp.bankingportal.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webapp.bankingportal.config.LoginLockoutProperties;
import com.webapp.bankingportal.entity.LoginAttempt;
import com.webapp.bankingportal.exception.AccountLockedException;
import com.webapp.bankingportal.repository.LoginAttemptRepository;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private final LoginAttemptRepository loginAttemptRepository;
    private final LoginLockoutProperties properties;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public void assertNotLocked(String accountNumber) {
        if (!properties.enabled()) {
            return;
        }

        val now = clock.instant();
        loginAttemptRepository.findByAccountNumber(accountNumber)
                .map(LoginAttempt::getLockedUntil)
                .filter(lockedUntil -> lockedUntil.isAfter(now))
                .ifPresent(lockedUntil -> {
                    val retryAfter = Duration.between(now, lockedUntil);
                    throw new AccountLockedException(
                            String.format(ApiMessages.ACCOUNT_LOCKED.getMessage(), ceilSeconds(retryAfter)),
                            retryAfter);
                });
    }

    @Override
    @Transactional
    public void recordFailure(String accountNumber) {
        if (!properties.enabled()) {
            return;
        }

        val now = clock.instant();
        val attempt = loginAttemptRepository.findForUpdateByAccountNumber(accountNumber)
                .orElseGet(() -> new LoginAttempt(accountNumber));

        if (isStale(attempt, now)) {
            attempt.setFailedAttempts(0);
            attempt.setLockedUntil(null);
        }

        attempt.setFailedAttempts(attempt.getFailedAttempts() + 1);
        attempt.setLastFailureAt(now);

        val lockDuration = lockDurationFor(attempt.getFailedAttempts());
        if (!lockDuration.isZero()) {
            attempt.setLockedUntil(now.plus(lockDuration));
            log.warn("Account {} locked for {}s after {} consecutive failed logins",
                    accountNumber, lockDuration.toSeconds(), attempt.getFailedAttempts());
        }

        loginAttemptRepository.save(attempt);
    }

    @Override
    @Transactional
    public void reset(String accountNumber) {
        loginAttemptRepository.deleteByAccountNumber(accountNumber);
    }

    @Override
    public Duration lockDurationFor(int failedAttempts) {
        if (failedAttempts < properties.maxAttempts()) {
            return Duration.ZERO;
        }

        val exponent = failedAttempts - properties.maxAttempts();
        val maxMillis = properties.maxLockDuration().toMillis();
        val millis = properties.baseLockDuration().toMillis() * Math.pow(properties.backoffMultiplier(), exponent);

        if (Double.isNaN(millis) || millis >= maxMillis) {
            return properties.maxLockDuration();
        }
        return Duration.ofMillis((long) millis);
    }

    private boolean isStale(LoginAttempt attempt, Instant now) {
        val lastFailureAt = attempt.getLastFailureAt();
        return lastFailureAt != null
                && lastFailureAt.plus(properties.failureResetWindow()).isBefore(now);
    }

    private static long ceilSeconds(Duration duration) {
        val seconds = duration.toSeconds();
        return duration.toNanosPart() > 0 ? seconds + 1 : seconds;
    }
}
