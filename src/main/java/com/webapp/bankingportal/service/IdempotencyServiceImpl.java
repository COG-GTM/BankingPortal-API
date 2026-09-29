package com.webapp.bankingportal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.webapp.bankingportal.entity.IdempotencyRecord;
import com.webapp.bankingportal.exception.IdempotencyKeyConflictException;
import com.webapp.bankingportal.exception.IdempotencyKeyInvalidException;
import com.webapp.bankingportal.exception.IdempotencyKeyMismatchException;
import com.webapp.bankingportal.repository.IdempotencyRecordRepository;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class IdempotencyServiceImpl implements IdempotencyService {

    static final int MAX_KEY_LENGTH = 255;

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Duration ttl;

    @Autowired
    public IdempotencyServiceImpl(IdempotencyRecordRepository idempotencyRecordRepository,
            PlatformTransactionManager transactionManager,
            @Value("${idempotency.ttl:PT24H}") Duration ttl) {
        this(idempotencyRecordRepository, new TransactionTemplate(transactionManager), Clock.systemUTC(), ttl);
    }

    IdempotencyServiceImpl(IdempotencyRecordRepository idempotencyRecordRepository,
            TransactionTemplate transactionTemplate, Clock clock, Duration ttl) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.ttl = ttl;
    }

    @Override
    public ResponseEntity<String> execute(String accountNumber, String idempotencyKey, String requestHash,
            Supplier<ResponseEntity<String>> action) {
        if (idempotencyKey == null) {
            return action.get();
        }

        validateKey(idempotencyKey);

        try {
            return transactionTemplate.execute(status -> executeOnce(accountNumber, idempotencyKey, requestHash, action));
        } catch (DataIntegrityViolationException e) {
            // A concurrent request with the same key committed first.
            log.info("Concurrent request detected for idempotency key on account {}", accountNumber);
            return findActive(accountNumber, idempotencyKey)
                    .map(existing -> replay(existing, requestHash))
                    .orElseThrow(() -> new IdempotencyKeyConflictException(
                            ApiMessages.IDEMPOTENCY_KEY_CONFLICT_ERROR.getMessage()));
        }
    }

    private ResponseEntity<String> executeOnce(String accountNumber, String idempotencyKey, String requestHash,
            Supplier<ResponseEntity<String>> action) {
        val now = clock.instant();
        val existing = idempotencyRecordRepository.findByAccountNumberAndIdempotencyKey(accountNumber, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().isExpired(now)) {
                return replay(existing.get(), requestHash);
            }
            idempotencyRecordRepository.delete(existing.get());
            idempotencyRecordRepository.flush();
        }

        // Claim the key before running the action so concurrent duplicates block on the unique constraint.
        val record = idempotencyRecordRepository.saveAndFlush(
                new IdempotencyRecord(accountNumber, idempotencyKey, requestHash, now, now.plus(ttl)));

        val response = action.get();
        record.setResponseStatus(response.getStatusCode().value());
        record.setResponseBody(response.getBody());
        idempotencyRecordRepository.save(record);
        return response;
    }

    private Optional<IdempotencyRecord> findActive(String accountNumber, String idempotencyKey) {
        val now = clock.instant();
        return idempotencyRecordRepository.findByAccountNumberAndIdempotencyKey(accountNumber, idempotencyKey)
                .filter(existing -> !existing.isExpired(now) && existing.getResponseStatus() != null);
    }

    private ResponseEntity<String> replay(IdempotencyRecord existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyMismatchException(ApiMessages.IDEMPOTENCY_KEY_MISMATCH_ERROR.getMessage());
        }

        if (existing.getResponseStatus() == null) {
            throw new IdempotencyKeyConflictException(ApiMessages.IDEMPOTENCY_KEY_CONFLICT_ERROR.getMessage());
        }

        return ResponseEntity.status(existing.getResponseStatus())
                .header(IDEMPOTENT_REPLAYED_HEADER, "true")
                .body(existing.getResponseBody());
    }

    private static void validateKey(String idempotencyKey) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new IdempotencyKeyInvalidException(
                    String.format(ApiMessages.IDEMPOTENCY_KEY_INVALID_ERROR.getMessage(), MAX_KEY_LENGTH));
        }
    }

    @Override
    public String hashRequest(String... parts) {
        try {
            val digest = MessageDigest.getInstance("SHA-256");
            for (val part : parts) {
                digest.update(String.valueOf(part).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    @Transactional
    @Scheduled(fixedDelayString = "${idempotency.purge-interval:PT1H}")
    public int purgeExpired() {
        val deleted = idempotencyRecordRepository.deleteExpired(clock.instant());
        if (deleted > 0) {
            log.info("Purged {} expired idempotency records", deleted);
        }
        return deleted;
    }
}
