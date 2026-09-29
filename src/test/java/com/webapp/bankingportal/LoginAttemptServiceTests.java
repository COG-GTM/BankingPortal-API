package com.webapp.bankingportal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.webapp.bankingportal.config.LoginLockoutProperties;
import com.webapp.bankingportal.entity.LoginAttempt;
import com.webapp.bankingportal.exception.AccountLockedException;
import com.webapp.bankingportal.repository.LoginAttemptRepository;
import com.webapp.bankingportal.service.LoginAttemptServiceImpl;

public class LoginAttemptServiceTests {

    private static final String ACCOUNT = "abc123";

    private final Map<String, LoginAttempt> store = new HashMap<>();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private LoginAttemptRepository repository;

    @BeforeEach
    void setUp() {
        repository = mock(LoginAttemptRepository.class);
        when(repository.findByAccountNumber(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.<String>getArgument(0))));
        when(repository.findForUpdateByAccountNumber(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.<String>getArgument(0))));
        when(repository.save(any(LoginAttempt.class))).thenAnswer(inv -> {
            LoginAttempt attempt = inv.getArgument(0);
            store.put(attempt.getAccountNumber(), attempt);
            return attempt;
        });
        doAnswer(inv -> store.remove(inv.<String>getArgument(0)))
                .when(repository).deleteByAccountNumber(anyString());
    }

    private LoginAttemptServiceImpl service(boolean enabled) {
        return new LoginAttemptServiceImpl(repository, new LoginLockoutProperties(
                enabled, 3, Duration.ofMinutes(1), 2.0, Duration.ofMinutes(10), Duration.ofHours(1)), clock);
    }

    private static AccountLockedException assertLocked(LoginAttemptServiceImpl service) {
        return assertThrows(AccountLockedException.class, () -> service.assertNotLocked(ACCOUNT));
    }

    @Test
    public void test_lock_duration_grows_exponentially_and_is_capped() {
        var service = service(true);
        assertEquals(Duration.ZERO, service.lockDurationFor(1));
        assertEquals(Duration.ZERO, service.lockDurationFor(2));
        assertEquals(Duration.ofMinutes(1), service.lockDurationFor(3));
        assertEquals(Duration.ofMinutes(2), service.lockDurationFor(4));
        assertEquals(Duration.ofMinutes(4), service.lockDurationFor(5));
        assertEquals(Duration.ofMinutes(8), service.lockDurationFor(6));
        assertEquals(Duration.ofMinutes(10), service.lockDurationFor(7));
        assertEquals(Duration.ofMinutes(10), service.lockDurationFor(10_000));
    }

    @Test
    public void test_not_locked_below_threshold() {
        var service = service(true);
        service.recordFailure(ACCOUNT);
        service.recordFailure(ACCOUNT);
        assertDoesNotThrow(() -> service.assertNotLocked(ACCOUNT));
    }

    @Test
    public void test_locks_at_threshold_and_unlocks_after_duration() {
        var service = service(true);
        for (int i = 0; i < 3; i++) {
            service.recordFailure(ACCOUNT);
        }

        assertEquals(Duration.ofMinutes(1), assertLocked(service).getRetryAfter());

        clock.advance(Duration.ofSeconds(59));
        assertEquals(Duration.ofSeconds(1), assertLocked(service).getRetryAfter());

        clock.advance(Duration.ofSeconds(1));
        assertDoesNotThrow(() -> service.assertNotLocked(ACCOUNT));
    }

    @Test
    public void test_each_failure_after_lock_expiry_doubles_lock() {
        var service = service(true);
        for (int i = 0; i < 3; i++) {
            service.recordFailure(ACCOUNT);
        }
        clock.advance(Duration.ofMinutes(1));

        service.recordFailure(ACCOUNT);
        assertEquals(Duration.ofMinutes(2), assertLocked(service).getRetryAfter());
        clock.advance(Duration.ofMinutes(2));

        service.recordFailure(ACCOUNT);
        assertEquals(Duration.ofMinutes(4), assertLocked(service).getRetryAfter());
    }

    @Test
    public void test_reset_clears_failures_and_lock() {
        var service = service(true);
        for (int i = 0; i < 3; i++) {
            service.recordFailure(ACCOUNT);
        }
        service.reset(ACCOUNT);

        assertDoesNotThrow(() -> service.assertNotLocked(ACCOUNT));
        service.recordFailure(ACCOUNT);
        assertEquals(1, store.get(ACCOUNT).getFailedAttempts());
    }

    @Test
    public void test_failures_older_than_reset_window_are_forgotten() {
        var service = service(true);
        service.recordFailure(ACCOUNT);
        service.recordFailure(ACCOUNT);

        clock.advance(Duration.ofHours(1).plusSeconds(1));
        service.recordFailure(ACCOUNT);

        assertEquals(1, store.get(ACCOUNT).getFailedAttempts());
        assertDoesNotThrow(() -> service.assertNotLocked(ACCOUNT));
    }

    @Test
    public void test_disabled_never_locks() {
        var service = service(false);
        for (int i = 0; i < 10; i++) {
            service.recordFailure(ACCOUNT);
        }
        assertDoesNotThrow(() -> service.assertNotLocked(ACCOUNT));
        assertEquals(0, store.size());
    }

    @Test
    public void test_invalid_properties_are_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new LoginLockoutProperties(
                true, 0, Duration.ofMinutes(1), 2.0, Duration.ofMinutes(10), Duration.ofHours(1)));
        assertThrows(IllegalArgumentException.class, () -> new LoginLockoutProperties(
                true, 3, Duration.ofMinutes(1), 0.5, Duration.ofMinutes(10), Duration.ofHours(1)));
        assertThrows(IllegalArgumentException.class, () -> new LoginLockoutProperties(
                true, 3, Duration.ZERO, 2.0, Duration.ofMinutes(10), Duration.ofHours(1)));
        assertThrows(IllegalArgumentException.class, () -> new LoginLockoutProperties(
                true, 3, Duration.ofMinutes(10), 2.0, Duration.ofMinutes(1), Duration.ofHours(1)));
    }
}
