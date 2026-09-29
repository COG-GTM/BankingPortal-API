package com.webapp.bankingportal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.webapp.bankingportal.entity.IdempotencyRecord;
import com.webapp.bankingportal.exception.IdempotencyKeyConflictException;
import com.webapp.bankingportal.exception.IdempotencyKeyInvalidException;
import com.webapp.bankingportal.exception.IdempotencyKeyMismatchException;
import com.webapp.bankingportal.repository.IdempotencyRecordRepository;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceImplTests {

    private static final String ACCOUNT = "abc123";
    private static final String KEY = "key-1";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private IdempotencyRecordRepository repository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private IdempotencyServiceImpl service;
    private final AtomicInteger executions = new AtomicInteger();

    @BeforeEach
    void setUp() {
        service = new IdempotencyServiceImpl(repository, new TransactionTemplate(transactionManager),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(24));
    }

    private ResponseEntity<String> action() {
        executions.incrementAndGet();
        return ResponseEntity.ok("done");
    }

    private IdempotencyRecord completedRecord(String hash, Instant expiresAt) {
        IdempotencyRecord record = new IdempotencyRecord(ACCOUNT, KEY, hash, NOW.minusSeconds(60), expiresAt);
        record.setResponseStatus(200);
        record.setResponseBody("done");
        return record;
    }

    @Test
    void null_key_runs_action_without_persistence() {
        ResponseEntity<String> response = service.execute(ACCOUNT, null, "h", this::action);

        assertEquals("done", response.getBody());
        assertEquals(1, executions.get());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void first_request_persists_key_with_ttl_and_response() {
        when(repository.findByAccountNumberAndIdempotencyKey(ACCOUNT, KEY)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<String> response = service.execute(ACCOUNT, KEY, "h", this::action);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, executions.get());
        verify(repository).save(argThat(record ->
                record.getExpiresAt().equals(NOW.plus(Duration.ofHours(24)))
                        && record.getResponseStatus() == 200
                        && "done".equals(record.getResponseBody())));
    }

    @Test
    void retry_with_active_key_replays_without_running_action() {
        when(repository.findByAccountNumberAndIdempotencyKey(ACCOUNT, KEY))
                .thenReturn(Optional.of(completedRecord("h", NOW.plusSeconds(60))));

        ResponseEntity<String> response = service.execute(ACCOUNT, KEY, "h", this::action);

        assertEquals("done", response.getBody());
        assertEquals("true", response.getHeaders().getFirst(IdempotencyService.IDEMPOTENT_REPLAYED_HEADER));
        assertEquals(0, executions.get());
    }

    @Test
    void retry_with_different_payload_is_rejected() {
        when(repository.findByAccountNumberAndIdempotencyKey(ACCOUNT, KEY))
                .thenReturn(Optional.of(completedRecord("h", NOW.plusSeconds(60))));

        assertThrows(IdempotencyKeyMismatchException.class,
                () -> service.execute(ACCOUNT, KEY, "other", this::action));
        assertEquals(0, executions.get());
    }

    @Test
    void expired_key_is_replaced_and_action_runs_again() {
        IdempotencyRecord expired = completedRecord("h", NOW);
        when(repository.findByAccountNumberAndIdempotencyKey(ACCOUNT, KEY)).thenReturn(Optional.of(expired));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.execute(ACCOUNT, KEY, "h", this::action);

        verify(repository).delete(expired);
        assertEquals(1, executions.get());
    }

    @Test
    void failed_action_propagates_and_does_not_store_response() {
        when(repository.findByAccountNumberAndIdempotencyKey(ACCOUNT, KEY)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(IllegalStateException.class, () -> service.execute(ACCOUNT, KEY, "h", () -> {
            throw new IllegalStateException("boom");
        }));
        verify(repository, never()).save(any());
        verify(transactionManager).rollback(any());
    }

    @Test
    void concurrent_duplicate_replays_committed_response() {
        when(repository.findByAccountNumberAndIdempotencyKey(ACCOUNT, KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(completedRecord("h", NOW.plusSeconds(60))));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        ResponseEntity<String> response = service.execute(ACCOUNT, KEY, "h", this::action);

        assertEquals("true", response.getHeaders().getFirst(IdempotencyService.IDEMPOTENT_REPLAYED_HEADER));
        assertEquals(0, executions.get());
        verify(repository, times(2)).findByAccountNumberAndIdempotencyKey(ACCOUNT, KEY);
    }

    @Test
    void concurrent_duplicate_without_committed_record_is_conflict() {
        when(repository.findByAccountNumberAndIdempotencyKey(ACCOUNT, KEY)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(IdempotencyKeyConflictException.class,
                () -> service.execute(ACCOUNT, KEY, "h", this::action));
        assertEquals(0, executions.get());
    }

    @Test
    void blank_or_oversized_key_is_rejected() {
        assertThrows(IdempotencyKeyInvalidException.class, () -> service.execute(ACCOUNT, "  ", "h", this::action));
        assertThrows(IdempotencyKeyInvalidException.class,
                () -> service.execute(ACCOUNT, "k".repeat(256), "h", this::action));
        assertEquals(0, executions.get());
    }

    @Test
    void hash_is_stable_and_field_separated() {
        assertEquals(service.hashRequest("a", "bc"), service.hashRequest("a", "bc"));
        assertNotEquals(service.hashRequest("a", "bc"), service.hashRequest("ab", "c"));
    }
}
