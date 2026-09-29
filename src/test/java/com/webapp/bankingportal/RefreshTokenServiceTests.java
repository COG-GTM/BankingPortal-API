package com.webapp.bankingportal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.RefreshToken;
import com.webapp.bankingportal.entity.Token;
import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.exception.InvalidTokenException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.RefreshTokenRepository;
import com.webapp.bankingportal.repository.TokenRepository;
import com.webapp.bankingportal.repository.UserRepository;
import com.webapp.bankingportal.service.RefreshTokenService;
import com.webapp.bankingportal.service.RefreshTokenServiceImpl;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.val;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:refresh-service;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@Import(RefreshTokenServiceImpl.class)
public class RefreshTokenServiceTests {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    private Account account;

    @BeforeEach
    public void setUp() {
        account = createAccount("100001", "a@example.com", "+14155550101");
    }

    @Test
    public void test_issue_stores_only_token_hash() {
        val rawToken = refreshTokenService.issue(account);

        val stored = refreshTokenRepository.findAll();
        Assertions.assertEquals(1, stored.size());
        Assertions.assertNotEquals(rawToken, stored.get(0).getTokenHash());
        Assertions.assertFalse(stored.get(0).isRevoked());
    }

    @Test
    public void test_rotate_revokes_presented_token_and_issues_successor_in_same_family() throws Exception {
        val original = refreshTokenService.issue(account);

        val rotation = refreshTokenService.rotate(original);

        Assertions.assertEquals(account.getAccountNumber(), rotation.accountNumber());
        Assertions.assertNotEquals(original, rotation.refreshToken());

        val family = refreshTokenRepository.findAll();
        Assertions.assertEquals(2, family.size());
        Assertions.assertEquals(family.get(0).getFamilyId(), family.get(1).getFamilyId());

        val revoked = family.stream().filter(RefreshToken::isRevoked).toList();
        Assertions.assertEquals(1, revoked.size());
        Assertions.assertNotNull(revoked.get(0).getReplacedByHash());

        Assertions.assertDoesNotThrow(() -> refreshTokenService.rotate(rotation.refreshToken()));
    }

    @Test
    public void test_rotate_with_reused_token_revokes_family_and_access_tokens() throws Exception {
        val otherSession = refreshTokenService.issue(account);
        val original = refreshTokenService.issue(account);
        val successor = refreshTokenService.rotate(original).refreshToken();
        tokenRepository.save(new Token("access-token", new Date(System.currentTimeMillis() + 60_000), account));

        val reuse = Assertions.assertThrows(InvalidTokenException.class,
                () -> refreshTokenService.rotate(original));
        Assertions.assertEquals(ApiMessages.REFRESH_TOKEN_REUSED_ERROR.getMessage(), reuse.getMessage());

        val successorFailure = Assertions.assertThrows(InvalidTokenException.class,
                () -> refreshTokenService.rotate(successor));
        Assertions.assertEquals(ApiMessages.REFRESH_TOKEN_REUSED_ERROR.getMessage(), successorFailure.getMessage());

        val compromisedFamily = findByRaw(original).getFamilyId();
        Assertions.assertTrue(refreshTokenRepository.findAllByFamilyId(compromisedFamily).stream()
                .allMatch(RefreshToken::isRevoked));
        Assertions.assertEquals(0, tokenRepository.findAllByAccount(account).length);

        Assertions.assertFalse(findByRaw(otherSession).isRevoked());
        Assertions.assertDoesNotThrow(() -> refreshTokenService.rotate(otherSession));
    }

    @Test
    public void test_rotate_with_reused_token_does_not_affect_other_accounts() throws Exception {
        val otherAccount = createAccount("100002", "b@example.com", "+14155550102");
        val otherToken = refreshTokenService.issue(otherAccount);
        tokenRepository.save(new Token("other-access-token", new Date(System.currentTimeMillis() + 60_000),
                otherAccount));

        val original = refreshTokenService.issue(account);
        refreshTokenService.rotate(original);
        Assertions.assertThrows(InvalidTokenException.class, () -> refreshTokenService.rotate(original));

        Assertions.assertFalse(findByRaw(otherToken).isRevoked());
        Assertions.assertNotNull(tokenRepository.findByToken("other-access-token"));
    }

    @Test
    public void test_rotate_with_expired_token() {
        val rawToken = refreshTokenService.issue(account);
        val stored = refreshTokenRepository.findAll().get(0);
        stored.setExpiresAt(Instant.now().minusSeconds(1));
        refreshTokenRepository.saveAndFlush(stored);

        val ex = Assertions.assertThrows(InvalidTokenException.class, () -> refreshTokenService.rotate(rawToken));
        Assertions.assertEquals(ApiMessages.REFRESH_TOKEN_EXPIRED_ERROR.getMessage(), ex.getMessage());
        Assertions.assertTrue(findByRaw(rawToken).isRevoked());
    }

    @Test
    public void test_rotate_with_unknown_or_empty_token() {
        for (val rawToken : new String[] { "unknown", "", null }) {
            val ex = Assertions.assertThrows(InvalidTokenException.class,
                    () -> refreshTokenService.rotate(rawToken));
            Assertions.assertEquals(ApiMessages.REFRESH_TOKEN_INVALID_ERROR.getMessage(), ex.getMessage());
        }
    }

    @Test
    public void test_revoke_revokes_whole_family() throws Exception {
        val original = refreshTokenService.issue(account);
        val successor = refreshTokenService.rotate(original).refreshToken();

        refreshTokenService.revoke(successor);

        Assertions.assertTrue(refreshTokenRepository.findAll().stream().allMatch(RefreshToken::isRevoked));
        Assertions.assertThrows(InvalidTokenException.class, () -> refreshTokenService.rotate(successor));
    }

    @Test
    public void test_revoke_with_unknown_token_is_noop() {
        refreshTokenService.issue(account);
        refreshTokenService.revoke("unknown");
        refreshTokenService.revoke(null);
        Assertions.assertTrue(refreshTokenRepository.findAll().stream().noneMatch(RefreshToken::isRevoked));
    }

    @Test
    public void test_revoke_all_for_account() {
        refreshTokenService.issue(account);
        refreshTokenService.issue(account);
        val otherAccount = createAccount("100003", "c@example.com", "+14155550103");
        val otherToken = refreshTokenService.issue(otherAccount);

        refreshTokenService.revokeAllForAccount(account);

        val stored = refreshTokenRepository.findAll();
        Assertions.assertEquals(2, stored.stream().filter(RefreshToken::isRevoked).count());
        Assertions.assertFalse(findByRaw(otherToken).isRevoked());
    }

    private RefreshToken findByRaw(String rawToken) {
        return refreshTokenRepository.findAll().stream()
                .filter(t -> t.getTokenHash().equals(sha256(rawToken)))
                .findFirst()
                .orElseThrow();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Account createAccount(String accountNumber, String email, String phoneNumber) {
        val user = new User();
        user.setName("Test User");
        user.setPassword("encoded-password");
        user.setEmail(email);
        user.setCountryCode("US");
        user.setPhoneNumber(phoneNumber);
        user.setAddress("1 Main St");
        userRepository.save(user);

        val newAccount = new Account();
        newAccount.setAccountNumber(accountNumber);
        newAccount.setUser(user);
        return accountRepository.save(newAccount);
    }

}
