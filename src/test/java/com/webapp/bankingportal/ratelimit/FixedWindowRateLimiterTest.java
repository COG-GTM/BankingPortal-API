package com.webapp.bankingportal.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private MutableClock clock;
    private FixedWindowRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        limiter = new FixedWindowRateLimiter(clock);
    }

    @Test
    void allowsRequestsUpToLimitThenRejects() {
        for (int i = 1; i <= 3; i++) {
            final FixedWindowRateLimiter.Decision decision = limiter.tryAcquire("k", 3, WINDOW);
            assertTrue(decision.allowed());
            assertEquals(3, decision.limit());
            assertEquals(3 - i, decision.remaining());
        }

        final FixedWindowRateLimiter.Decision rejected = limiter.tryAcquire("k", 3, WINDOW);
        assertFalse(rejected.allowed());
        assertEquals(0, rejected.remaining());
        assertEquals(60, rejected.retryAfterSeconds());
    }

    @Test
    void retryAfterCountsDownToWindowEnd() {
        limiter.tryAcquire("k", 1, WINDOW);
        clock.advance(Duration.ofSeconds(45).plusMillis(500));

        final FixedWindowRateLimiter.Decision rejected = limiter.tryAcquire("k", 1, WINDOW);
        assertFalse(rejected.allowed());
        assertEquals(15, rejected.retryAfterSeconds());
    }

    @Test
    void resetsAfterWindowElapses() {
        limiter.tryAcquire("k", 1, WINDOW);
        assertFalse(limiter.tryAcquire("k", 1, WINDOW).allowed());

        clock.advance(WINDOW);

        assertTrue(limiter.tryAcquire("k", 1, WINDOW).allowed());
    }

    @Test
    void rejectedRequestsDoNotExtendWindow() {
        limiter.tryAcquire("k", 1, WINDOW);
        for (int i = 0; i < 5; i++) {
            clock.advance(Duration.ofSeconds(10));
            assertFalse(limiter.tryAcquire("k", 1, WINDOW).allowed());
        }
        clock.advance(Duration.ofSeconds(10));
        assertTrue(limiter.tryAcquire("k", 1, WINDOW).allowed());
    }

    @Test
    void keysAreIndependent() {
        limiter.tryAcquire("a", 1, WINDOW);
        assertFalse(limiter.tryAcquire("a", 1, WINDOW).allowed());
        assertTrue(limiter.tryAcquire("b", 1, WINDOW).allowed());
    }

    @Test
    void zeroLimitRejectsEverything() {
        assertFalse(limiter.tryAcquire("k", 0, WINDOW).allowed());
    }

    @Test
    void expiredWindowsAreEvicted() {
        limiter.tryAcquire("a", 5, WINDOW);
        limiter.tryAcquire("b", 5, WINDOW);
        assertEquals(2, limiter.trackedKeys());

        clock.advance(WINDOW);
        limiter.tryAcquire("c", 5, WINDOW);

        assertEquals(1, limiter.trackedKeys());
    }

    @Test
    void concurrentRequestsNeverExceedLimit() throws InterruptedException {
        final int limit = 50;
        final AtomicInteger allowed = new AtomicInteger();
        final ExecutorService executor = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 500; i++) {
            executor.submit(() -> {
                if (limiter.tryAcquire("k", limit, WINDOW).allowed()) {
                    allowed.incrementAndGet();
                }
            });
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(limit, allowed.get());
    }
}
