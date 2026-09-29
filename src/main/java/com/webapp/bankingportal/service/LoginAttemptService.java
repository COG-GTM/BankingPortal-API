package com.webapp.bankingportal.service;

import java.time.Duration;

import com.webapp.bankingportal.exception.AccountLockedException;

public interface LoginAttemptService {

    /**
     * @throws AccountLockedException if the account is currently locked out
     */
    void assertNotLocked(String accountNumber);

    void recordFailure(String accountNumber);

    void reset(String accountNumber);

    /**
     * Lock duration applied once {@code failedAttempts} consecutive failures
     * have been recorded; {@link Duration#ZERO} below the threshold.
     */
    Duration lockDurationFor(int failedAttempts);
}
