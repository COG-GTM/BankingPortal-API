package com.webapp.bankingportal;

import java.util.Date;

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
    public void test_invalidate_all_tokens_removes_every_session_of_account() throws Exception {
        val userDetails = createAndLoginUser();
        val firstToken = userDetails.get("token");
        val secondToken = generateToken(userDetails.get("accountNumber"), userDetails.get("password"),
                new Date(System.currentTimeMillis() + 3_600_000));
        tokenService.saveToken(secondToken);
        val otherAccountToken = createAndLoginUser().get("token");

        tokenService.invalidateAllTokens(tokenRepository.findByToken(firstToken).getAccount());

        Assertions.assertNull(tokenRepository.findByToken(firstToken));
        Assertions.assertNull(tokenRepository.findByToken(secondToken));
        Assertions.assertNotNull(tokenRepository.findByToken(otherAccountToken));
    }

    @Test
    public void test_invalidate_other_tokens_keeps_current_session() throws Exception {
        val userDetails = createAndLoginUser();
        val currentToken = userDetails.get("token");
        val otherToken = generateToken(userDetails.get("accountNumber"), userDetails.get("password"),
                new Date(System.currentTimeMillis() + 3_600_000));
        tokenService.saveToken(otherToken);
        val otherAccountToken = createAndLoginUser().get("token");

        tokenService.invalidateOtherTokens(currentToken);

        Assertions.assertNotNull(tokenRepository.findByToken(currentToken));
        Assertions.assertNull(tokenRepository.findByToken(otherToken));
        Assertions.assertNotNull(tokenRepository.findByToken(otherAccountToken));
    }

}
