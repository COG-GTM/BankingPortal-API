package com.webapp.bankingportal;

import java.util.Date;

import static org.springframework.security.core.userdetails.User.withUsername;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import com.webapp.bankingportal.exception.InvalidTokenException;
import com.webapp.bankingportal.repository.TokenRepository;
import com.webapp.bankingportal.util.ApiMessages;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import lombok.val;

public class TokenServiceTests extends BaseTest {

    @Autowired
    TokenRepository tokenRepository;

    @Test
    public void test_validate_token_with_valid_token() throws Exception {
        val token = createAndLoginUser().get("token");
        tokenService.validateToken(token);
        tokenService.invalidateToken(token);
    }

    @Test
    public void test_validate_token_with_invalid_token() throws Exception {
        val token = generateToken(getRandomAccountNumber(), getRandomPassword());

        Assertions.assertThrows(InvalidTokenException.class,
                () -> tokenService.validateToken(token),
                ApiMessages.TOKEN_NOT_FOUND_ERROR.getMessage());
    }

    @Test
    public void test_invalidate_token_with_valid_token() throws Exception {
        val token = createAndLoginUser().get("token");
        Assertions.assertNotNull(tokenRepository.findByToken(token));

        tokenService.invalidateToken(token);
        Assertions.assertNull(tokenRepository.findByToken(token));
    }

    @Test
    public void test_invalidate_token_with_invalid_token() throws Exception {
        val token = generateToken(getRandomAccountNumber(), getRandomPassword());
        tokenService.invalidateToken(token);
    }

    @Test
    public void test_save_token_with_valid_token() throws Exception {
        val accountDetails = createAccount();
        val token = generateToken(
                accountDetails.get("accountNumber"),
                accountDetails.get("password"));

        tokenService.saveToken(token);
        Assertions.assertNotNull(tokenRepository.findByToken(token));

        tokenService.invalidateToken(token);
        Assertions.assertNull(tokenRepository.findByToken(token));
    }

    @Test
    public void test_save_token_with_duplicate_token() throws Exception {
        val token = createAndLoginUser().get("token");
        Assertions.assertNotNull(tokenRepository.findByToken(token));
        Assertions.assertThrows(InvalidTokenException.class,
                () -> tokenService.saveToken(token),
                ApiMessages.TOKEN_ALREADY_EXISTS_ERROR.getMessage());

        tokenService.invalidateToken(token);
        Assertions.assertNull(tokenRepository.findByToken(token));
    }

    @Test
    public void test_get_username_from_token_with_valid_token() throws Exception {
        val userDetails = createAndLoginUser();
        val token = userDetails.get("token");
        val accountNumber = userDetails.get("accountNumber");
        val username = tokenService.getUsernameFromToken(token);

        Assertions.assertEquals(accountNumber, username);
    }

    @Test
    public void test_get_username_from_token_with_expired_token() {
        val token = generateToken(
                getRandomAccountNumber(), getRandomPassword(), new Date());

        Assertions.assertThrows(InvalidTokenException.class,
                () -> tokenService.getUsernameFromToken(token),
                ApiMessages.TOKEN_EXPIRED_ERROR.getMessage());
    }

    @Test
    public void test_get_username_from_token_with_unsigned_token() {
        val token = Jwts.builder().setSubject(getRandomAccountNumber())
                .setIssuedAt(new Date()).compact();

        Assertions.assertThrows(InvalidTokenException.class,
                () -> tokenService.getUsernameFromToken(token),
                ApiMessages.TOKEN_UNSUPPORTED_ERROR.getMessage());
    }

    @Test
    public void test_get_username_from_token_with_malformed_token() {
        val token = "malformed";

        Assertions.assertThrows(InvalidTokenException.class,
                () -> tokenService.getUsernameFromToken(token),
                ApiMessages.TOKEN_MALFORMED_ERROR.getMessage());
    }

    @Test
    public void test_get_username_from_token_with_invalid_signature() {
        val token = Jwts.builder().setSubject(getRandomAccountNumber())
                .setIssuedAt(new Date())
                .signWith(SignatureAlgorithm.HS256, "invalid")
                .compact();

        Assertions.assertThrows(InvalidTokenException.class,
                () -> tokenService.getUsernameFromToken(token),
                ApiMessages.TOKEN_SIGNATURE_INVALID_ERROR.getMessage());
    }

    @Test
    public void test_get_username_from_token_with_empty_token() {
        Assertions.assertThrows(InvalidTokenException.class,
                () -> tokenService.getUsernameFromToken(null),
                ApiMessages.TOKEN_EMPTY_ERROR.getMessage());
    }

    @Test
    public void test_generate_token_twice_yields_distinct_tokens() {
        val userDetails = withUsername(getRandomAccountNumber()).password(getRandomPassword()).build();
        val expiry = new Date(System.currentTimeMillis() + 60_000);

        Assertions.assertNotEquals(
                tokenService.generateToken(userDetails, expiry),
                tokenService.generateToken(userDetails, expiry));
    }

    @Test
    public void test_validate_token_with_tampered_signature() throws Exception {
        val token = createAndLoginUser().get("token");
        val index = token.lastIndexOf('.') + 10;
        val replacement = token.charAt(index) == 'A' ? 'B' : 'A';
        val tampered = token.substring(0, index) + replacement + token.substring(index + 1);

        val exception = Assertions.assertThrows(InvalidTokenException.class,
                () -> tokenService.validateToken(tampered));
        Assertions.assertEquals(ApiMessages.TOKEN_SIGNATURE_INVALID_ERROR.getMessage(), exception.getMessage());
    }

    @Test
    public void test_validate_token_with_persisted_expired_token() throws Exception {
        val accountDetails = createAccount();
        val token = generateToken(
                accountDetails.get("accountNumber"),
                accountDetails.get("password"),
                new Date(System.currentTimeMillis() + 1000));
        tokenService.saveToken(token);
        Assertions.assertNotNull(tokenRepository.findByToken(token));

        Thread.sleep(2000);

        val exception = Assertions.assertThrows(InvalidTokenException.class,
                () -> tokenService.validateToken(token));
        Assertions.assertEquals(ApiMessages.TOKEN_EXPIRED_ERROR.getMessage(), exception.getMessage());
        Assertions.assertNull(tokenRepository.findByToken(token));
    }

    @Test
    public void test_validate_token_after_invalidate() throws Exception {
        val token = createAndLoginUser().get("token");
        tokenService.invalidateToken(token);

        val exception = Assertions.assertThrows(InvalidTokenException.class,
                () -> tokenService.validateToken(token));
        Assertions.assertEquals(ApiMessages.TOKEN_NOT_FOUND_ERROR.getMessage(), exception.getMessage());
    }

    @Test
    public void test_invalidate_all_tokens_revokes_every_session() throws Exception {
        val accountDetails = createAccount();
        val accountNumber = accountDetails.get("accountNumber");
        val first = generateToken(accountNumber, accountDetails.get("password"));
        val second = generateToken(accountNumber, accountDetails.get("password"));
        tokenService.saveToken(first);
        tokenService.saveToken(second);

        val otherToken = createAndLoginUser().get("token");

        tokenService.invalidateAllTokens(accountNumber);

        Assertions.assertNull(tokenRepository.findByToken(first));
        Assertions.assertNull(tokenRepository.findByToken(second));
        Assertions.assertNotNull(tokenRepository.findByToken(otherToken));
    }

    @Test
    public void test_refresh_token_rotates_token() throws Exception {
        val userDetails = createAndLoginUser();
        val oldToken = userDetails.get("token");

        val newToken = tokenService.refreshToken(oldToken);

        Assertions.assertNotEquals(oldToken, newToken);
        Assertions.assertEquals(userDetails.get("accountNumber"), tokenService.getUsernameFromToken(newToken));
        tokenService.validateToken(newToken);
        Assertions.assertNull(tokenRepository.findByToken(oldToken));
    }

    @Test
    public void test_refresh_token_with_revoked_token() throws Exception {
        val token = createAndLoginUser().get("token");
        tokenService.invalidateToken(token);

        Assertions.assertThrows(InvalidTokenException.class,
                () -> tokenService.refreshToken(token));
    }

}
