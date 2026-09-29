package com.webapp.bankingportal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webapp.bankingportal.dto.RefreshTokenRotation;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.RefreshToken;
import com.webapp.bankingportal.exception.InvalidTokenException;
import com.webapp.bankingportal.repository.RefreshTokenRepository;
import com.webapp.bankingportal.repository.TokenRepository;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenRepository tokenRepository;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    @Override
    @Transactional
    public String issue(Account account) {
        return createToken(account, UUID.randomUUID().toString(), Instant.now()).rawToken();
    }

    @Override
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public RefreshTokenRotation rotate(String rawToken) throws InvalidTokenException {
        val current = findByRawToken(rawToken)
                .orElseThrow(() -> new InvalidTokenException(ApiMessages.REFRESH_TOKEN_INVALID_ERROR.getMessage()));
        val account = current.getAccount();
        val now = Instant.now();

        if (current.isRevoked()) {
            log.warn("Refresh token reuse detected for account {} (family {}); revoking family and access tokens",
                    account.getAccountNumber(), current.getFamilyId());
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            tokenRepository.deleteAllByAccount(account);
            throw new InvalidTokenException(ApiMessages.REFRESH_TOKEN_REUSED_ERROR.getMessage());
        }

        if (current.isExpired(now)) {
            current.setRevokedAt(now);
            refreshTokenRepository.save(current);
            throw new InvalidTokenException(ApiMessages.REFRESH_TOKEN_EXPIRED_ERROR.getMessage());
        }

        val successor = createToken(account, current.getFamilyId(), now);
        current.setRevokedAt(now);
        current.setReplacedByHash(successor.entity().getTokenHash());
        refreshTokenRepository.save(current);

        return new RefreshTokenRotation(account.getAccountNumber(), successor.rawToken());
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        findByRawToken(rawToken)
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    @Override
    @Transactional
    public void revokeAllForAccount(Account account) {
        refreshTokenRepository.revokeAllByAccount(account, Instant.now());
    }

    private Optional<RefreshToken> findByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByTokenHash(hash(rawToken));
    }

    private IssuedToken createToken(Account account, String familyId, Instant now) {
        val bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        val rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        val entity = new RefreshToken(hash(rawToken), familyId, account, now,
                now.plusMillis(refreshExpiration));
        refreshTokenRepository.save(entity);

        return new IssuedToken(rawToken, entity);
    }

    static String hash(String rawToken) {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record IssuedToken(String rawToken, RefreshToken entity) {
    }

}
