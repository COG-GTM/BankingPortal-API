package com.webapp.bankingportal.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory fixed-window counter keyed by an arbitrary string. State is local
 * to the JVM, so limits apply per application instance.
 */
public class FixedWindowRateLimiter {

    public record Decision(boolean allowed, int limit, int remaining, long retryAfterSeconds) {
    }

    private record Window(long startMillis, long endMillis, int count, boolean lastAccepted) {
        boolean isExpired(long nowMillis) {
            return nowMillis >= endMillis;
        }
    }

    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong lastSweepMillis;

    public FixedWindowRateLimiter(Clock clock) {
        this.clock = clock;
        this.lastSweepMillis = new AtomicLong(clock.millis());
    }

    public Decision tryAcquire(String key, int maxRequests, Duration window) {
        final long now = clock.millis();
        final long windowMillis = window.toMillis();
        sweepExpired(now, windowMillis);

        final Window current = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.isExpired(now)) {
                return new Window(now, now + windowMillis, 1, maxRequests > 0);
            }
            if (existing.count() >= maxRequests) {
                return new Window(existing.startMillis(), existing.endMillis(), existing.count(), false);
            }
            return new Window(existing.startMillis(), existing.endMillis(), existing.count() + 1, true);
        });

        final int remaining = Math.max(0, maxRequests - current.count());
        if (current.lastAccepted()) {
            return new Decision(true, maxRequests, remaining, 0);
        }
        final long retryAfterSeconds = Math.max(1, (current.endMillis() - now + 999) / 1000);
        return new Decision(false, maxRequests, 0, retryAfterSeconds);
    }

    int trackedKeys() {
        return windows.size();
    }

    private void sweepExpired(long now, long windowMillis) {
        final long last = lastSweepMillis.get();
        if (now - last >= windowMillis && lastSweepMillis.compareAndSet(last, now)) {
            windows.values().removeIf(w -> w.isExpired(now));
        }
    }
}
