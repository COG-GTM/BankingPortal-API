package com.webapp.bankingportal.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.core.userdetails.User.withUsername;

import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.Token;
import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.exception.InvalidTokenException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TokenRepository;
import com.webapp.bankingportal.repository.UserRepository;
import com.webapp.bankingportal.util.ApiMessages;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@ExtendWith(MockitoExtension.class)
class TokenServiceImplUnitTests {

    private static final String SECRET = "DxRVQlXF1Z39bJuUk1VoqfWS82pKJukRrt5pNl6ODhuSaztDny33Fw/NsBBafh1reMgxaG0qwgm88HJ4t2lteg==";
    private static final long EXPIRATION_MS = 60_000;
    private static final String ACCOUNT_NUMBER = "abc123";

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private AccountRepository accountRepository;

    private TokenServiceImpl tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenServiceImpl(userRepository, tokenRepository, accountRepository);
        ReflectionTestUtils.setField(tokenService, "secret", SECRET);
        ReflectionTestUtils.setField(tokenService, "expiration", EXPIRATION_MS);
    }

    private static UserDetails userDetails() {
        return withUsername(ACCOUNT_NUMBER).password("secret").build();
    }

    @Test
    void generateToken_embedsSubjectAndConfiguredExpiration() throws InvalidTokenException {
        var before = System.currentTimeMillis();

        var token = tokenService.generateToken(userDetails());

        assertEquals(ACCOUNT_NUMBER, tokenService.getUsernameFromToken(token));
        var expiry = tokenService.getExpirationDateFromToken(token).getTime();
        assertTrue(expiry >= (before + EXPIRATION_MS) / 1000 * 1000);
        assertTrue(expiry <= System.currentTimeMillis() + EXPIRATION_MS);
    }

    @Test
    void generateToken_withExplicitExpiry() throws InvalidTokenException {
        var expiry = new Date((System.currentTimeMillis() + 120_000) / 1000 * 1000);

        var token = tokenService.generateToken(userDetails(), expiry);

        assertEquals(expiry, tokenService.getExpirationDateFromToken(token));
    }

    @Test
    void getUsernameFromToken_expiredTokenIsInvalidatedAndRejected() {
        var token = tokenService.generateToken(userDetails(), new Date(System.currentTimeMillis() - 1_000));
        when(tokenRepository.findByToken(token)).thenReturn(new Token());

        var ex = assertThrows(InvalidTokenException.class, () -> tokenService.getUsernameFromToken(token));

        assertEquals(ApiMessages.TOKEN_EXPIRED_ERROR.getMessage(), ex.getMessage());
        verify(tokenRepository).deleteByToken(token);
    }

    @Test
    void getUsernameFromToken_rejectsUnsignedToken() {
        var token = Jwts.builder().setSubject(ACCOUNT_NUMBER).setIssuedAt(new Date()).compact();

        var ex = assertThrows(InvalidTokenException.class, () -> tokenService.getUsernameFromToken(token));
        assertEquals(ApiMessages.TOKEN_UNSUPPORTED_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    void getUsernameFromToken_rejectsMalformedToken() {
        var ex = assertThrows(InvalidTokenException.class, () -> tokenService.getUsernameFromToken("malformed"));
        assertEquals(ApiMessages.TOKEN_MALFORMED_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    void getUsernameFromToken_rejectsTokenSignedWithOtherKey() {
        var otherKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
        var token = Jwts.builder().setSubject(ACCOUNT_NUMBER).signWith(otherKey, SignatureAlgorithm.HS512).compact();

        var ex = assertThrows(InvalidTokenException.class, () -> tokenService.getUsernameFromToken(token));
        assertEquals(ApiMessages.TOKEN_SIGNATURE_INVALID_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    void getUsernameFromToken_rejectsEmptyToken() {
        var ex = assertThrows(InvalidTokenException.class, () -> tokenService.getUsernameFromToken(null));
        assertEquals(ApiMessages.TOKEN_EMPTY_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    void loadUserByUsername_returnsUserDetailsForAccount() {
        var user = new User();
        user.setPassword("encoded");
        when(userRepository.findByAccountAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(user));

        var details = tokenService.loadUserByUsername(ACCOUNT_NUMBER);

        assertEquals(ACCOUNT_NUMBER, details.getUsername());
        assertEquals("encoded", details.getPassword());
    }

    @Test
    void loadUserByUsername_throwsWhenUserMissing() {
        when(userRepository.findByAccountAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> tokenService.loadUserByUsername(ACCOUNT_NUMBER));
    }

    @Test
    void saveToken_persistsTokenLinkedToAccount() throws InvalidTokenException {
        var token = tokenService.generateToken(userDetails());
        var account = new Account();
        account.setAccountNumber(ACCOUNT_NUMBER);
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(account);

        tokenService.saveToken(token);

        var captor = ArgumentCaptor.forClass(Token.class);
        verify(tokenRepository).save(captor.capture());
        assertEquals(token, captor.getValue().getToken());
        assertSame(account, captor.getValue().getAccount());
        assertEquals(tokenService.getExpirationDateFromToken(token), captor.getValue().getExpiryAt());
    }

    @Test
    void saveToken_rejectsDuplicateToken() {
        when(tokenRepository.findByToken("existing")).thenReturn(new Token());

        var ex = assertThrows(InvalidTokenException.class, () -> tokenService.saveToken("existing"));
        assertEquals(ApiMessages.TOKEN_ALREADY_EXISTS_ERROR.getMessage(), ex.getMessage());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void validateToken_acceptsKnownTokenAndRejectsUnknownToken() {
        when(tokenRepository.findByToken("known")).thenReturn(new Token());

        assertDoesNotThrow(() -> tokenService.validateToken("known"));
        var ex = assertThrows(InvalidTokenException.class, () -> tokenService.validateToken("unknown"));
        assertEquals(ApiMessages.TOKEN_NOT_FOUND_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    void invalidateToken_deletesOnlyExistingTokens() {
        when(tokenRepository.findByToken("known")).thenReturn(new Token());

        tokenService.invalidateToken("known");
        tokenService.invalidateToken("unknown");

        verify(tokenRepository).deleteByToken("known");
        verify(tokenRepository, never()).deleteByToken("unknown");
        verify(tokenRepository, never()).save(any());
        verify(accountRepository, never()).findByAccountNumber(anyString());
    }
}
