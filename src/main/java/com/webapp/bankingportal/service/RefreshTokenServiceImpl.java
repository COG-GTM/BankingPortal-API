package com.webapp.bankingportal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webapp.bankingportal.dto.TokenPair;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.RefreshToken;
import com.webapp.bankingportal.exception.InvalidTokenException;
import com.webapp.bankingportal.repository.AccountRepository;
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
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    private final TokenService tokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenRepository tokenRepository;
    private final AccountRepository accountRepository;

    @Override
    @Transactional
    public TokenPair issueTokenPair(String accountNumber) throws InvalidTokenException {
        val account = accountRepository.findByAccountNumber(accountNumber);
        if (account == null) {
            throw new InvalidTokenException(ApiMessages.TOKEN_INVALID_ERROR.getMessage());
        }

        return issue(account, UUID.randomUUID().toString());
    }

    @Override
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public TokenPair rotate(String refreshToken) throws InvalidTokenException {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidTokenException(ApiMessages.TOKEN_EMPTY_ERROR.getMessage());
        }

        val existing = refreshTokenRepository.findByTokenHashForUpdate(hash(refreshToken))
                .orElseThrow(() -> new InvalidTokenException(ApiMessages.REFRESH_TOKEN_INVALID_ERROR.getMessage()));

        if (existing.getUsedAt() != null) {
            log.warn("Refresh token reuse detected for account {}; revoking token family {}",
                    existing.getAccount().getAccountNumber(), existing.getFamilyId());
            revokeFamily(existing.getFamilyId());
            throw new InvalidTokenException(ApiMessages.REFRESH_TOKEN_REUSE_DETECTED_ERROR.getMessage());
        }

        if (existing.getRevokedAt() != null) {
            throw new InvalidTokenException(ApiMessages.REFRESH_TOKEN_INVALID_ERROR.getMessage());
        }

        val now = new Date();
        if (!existing.getExpiresAt().after(now)) {
            throw new InvalidTokenException(ApiMessages.TOKEN_EXPIRED_ERROR.getMessage());
        }

        existing.setUsedAt(now);
        refreshTokenRepository.save(existing);

        return issue(existing.getAccount(), existing.getFamilyId());
    }

    @Override
    @Transactional
    public void revokeFamily(String familyId) {
        refreshTokenRepository.revokeFamily(familyId, new Date());
        tokenRepository.deleteAllByFamilyId(familyId);
    }

    @Override
    @Transactional
    public void revokeFamilyOfAccessToken(String accessToken) {
        val token = tokenRepository.findByToken(accessToken);
        if (token != null && token.getFamilyId() != null) {
            revokeFamily(token.getFamilyId());
        }
    }

    private TokenPair issue(Account account, String familyId) throws InvalidTokenException {
        val userDetails = User.withUsername(account.getAccountNumber())
                .password("")
                .build();
        val accessToken = tokenService.generateToken(userDetails);
        tokenService.saveToken(accessToken, familyId);

        val rawRefreshToken = generateRawToken();
        val expiresAt = new Date(System.currentTimeMillis() + refreshExpiration);
        refreshTokenRepository.save(new RefreshToken(hash(rawRefreshToken), familyId, account, expiresAt));

        return new TokenPair(accessToken, rawRefreshToken);
    }

    private static String generateRawToken() {
        val bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String rawToken) {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

}
