package com.webapp.bankingportal.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.Date;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.Token;
import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.exception.InvalidTokenException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TokenRepository;
import com.webapp.bankingportal.repository.UserRepository;
import com.webapp.bankingportal.service.TokenServiceImpl;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import lombok.val;

class TokenServiceAuditLoggingTest {

    private static final long USER_ID = 42L;
    private static final String EMAIL = "jane.doe@example.com";
    private static final String ACCOUNT_NUMBER = "a7f3c9";
    private static final byte[] KEY_BYTES = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            .getBytes();

    private final TokenRepository tokenRepository = mock(TokenRepository.class);

    private TokenServiceImpl tokenService;
    private Account account;
    private LogCapture logs;

    @BeforeEach
    void setUp() {
        tokenService = new TokenServiceImpl(
                mock(UserRepository.class), tokenRepository, mock(AccountRepository.class), new SecurityAuditLogger());
        ReflectionTestUtils.setField(tokenService, "secret", Base64.getEncoder().encodeToString(KEY_BYTES));
        ReflectionTestUtils.setField(tokenService, "expiration", 60_000L);

        val user = new User();
        user.setId(USER_ID);
        user.setEmail(EMAIL);
        account = new Account();
        account.setAccountNumber(ACCOUNT_NUMBER);
        account.setUser(user);

        logs = new LogCapture();
    }

    @AfterEach
    void tearDown() {
        logs.close();
    }

    private String storedToken(Date expiry) {
        val token = Jwts.builder()
                .setSubject(ACCOUNT_NUMBER)
                .setIssuedAt(new Date(expiry.getTime() - 120_000L))
                .setExpiration(expiry)
                .signWith(Keys.hmacShaKeyFor(KEY_BYTES), SignatureAlgorithm.HS512)
                .compact();
        when(tokenRepository.findByToken(token)).thenReturn(new Token(token, expiry, account));
        return token;
    }

    @Test
    void logoutRevocation_isAuditedWithoutTokenOrAccountNumber() {
        val token = storedToken(new Date(System.currentTimeMillis() + 60_000L));

        tokenService.invalidateToken(token, TokenRevocationReason.LOGOUT);

        verify(tokenRepository).deleteByToken(token);
        assertThat(LogCapture.keyValues(logs.singleAuditEvent()))
                .containsEntry("event", "TOKEN_REVOKED")
                .containsEntry("reason", "LOGOUT")
                .containsEntry("user_id", String.valueOf(USER_ID));
        logs.assertNoneContain(token, ACCOUNT_NUMBER, EMAIL);
        logs.assertNoneContain(token.split("\\.")[1], token.split("\\.")[2]);
    }

    @Test
    void expiredTokenRevocation_isAuditedWithoutTokenOrAccountNumber() {
        val token = storedToken(new Date(System.currentTimeMillis() - 60_000L));

        assertThatThrownBy(() -> tokenService.getUsernameFromToken(token))
                .isInstanceOf(InvalidTokenException.class);

        verify(tokenRepository).deleteByToken(token);
        assertThat(LogCapture.keyValues(logs.singleAuditEvent()))
                .containsEntry("event", "TOKEN_REVOKED")
                .containsEntry("reason", "EXPIRED");
        logs.assertNoneContain(token, ACCOUNT_NUMBER, EMAIL);
    }

    @Test
    void unknownToken_isNotAudited() {
        val token = "not-a-stored-token";

        tokenService.invalidateToken(token, TokenRevocationReason.LOGOUT);

        verify(tokenRepository, never()).deleteByToken(token);
        assertThat(logs.auditEvents()).isEmpty();
    }

}
