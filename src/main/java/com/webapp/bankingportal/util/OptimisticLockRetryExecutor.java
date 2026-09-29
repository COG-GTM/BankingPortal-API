package com.webapp.bankingportal.util;

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.webapp.bankingportal.exception.ConcurrentUpdateException;

import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs a unit of work in its own transaction and re-runs it when it fails
 * with an optimistic locking conflict, up to a bounded number of attempts.
 * When invoked inside an already active transaction the work runs once,
 * since a conflict marks the enclosing transaction rollback-only.
 */
@Component
@Slf4j
public class OptimisticLockRetryExecutor {

    private final TransactionTemplate transactionTemplate;
    private final int maxAttempts;
    private final long backoffMillis;

    public OptimisticLockRetryExecutor(
            PlatformTransactionManager transactionManager,
            @Value("${bankingportal.optimistic-lock.max-attempts:3}") int maxAttempts,
            @Value("${bankingportal.optimistic-lock.backoff-ms:50}") long backoffMillis) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("bankingportal.optimistic-lock.max-attempts must be at least 1");
        }
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.maxAttempts = maxAttempts;
        this.backoffMillis = Math.max(0, backoffMillis);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void execute(Runnable work) {
        execute(() -> {
            work.run();
            return null;
        });
    }

    public <T> T execute(Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return work.get();
        }

        RuntimeException lastConflict = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return transactionTemplate.execute(status -> work.get());
            } catch (OptimisticLockingFailureException | OptimisticLockException ex) {
                lastConflict = ex;
                log.warn("Optimistic lock conflict on attempt {}/{}: {}", attempt, maxAttempts, ex.getMessage());
                if (attempt < maxAttempts) {
                    backoff(attempt);
                }
            }
        }

        throw new ConcurrentUpdateException(ApiMessages.ACCOUNT_CONCURRENT_UPDATE_ERROR.getMessage(), lastConflict);
    }

    private void backoff(int attempt) {
        if (backoffMillis == 0) {
            return;
        }
        try {
            Thread.sleep(backoffMillis * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ConcurrentUpdateException(ApiMessages.ACCOUNT_CONCURRENT_UPDATE_ERROR.getMessage(), ie);
        }
    }

}
