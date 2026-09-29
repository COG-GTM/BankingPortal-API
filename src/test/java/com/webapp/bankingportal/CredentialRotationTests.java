package com.webapp.bankingportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.jayway.jsonpath.JsonPath;
import com.webapp.bankingportal.dto.ChangePasswordRequest;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.Token;
import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.exception.InvalidPinException;
import com.webapp.bankingportal.exception.UserInvalidException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TokenRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.repository.UserRepository;
import com.webapp.bankingportal.security.CredentialPolicy;
import com.webapp.bankingportal.service.AccountServiceImpl;
import com.webapp.bankingportal.service.UserServiceImpl;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.val;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CredentialRotationTests {

    private static final String ACCOUNT_NUMBER = "a1b2c3";
    private static final String CURRENT_PASSWORD = "Tr0ub4dor&Horse";
    private static final String NEW_PASSWORD = "vX9#kLm2!qPz";
    private static final String CURRENT_PIN = "7351";

    @Spy
    private CredentialPolicy credentialPolicy = new CredentialPolicy(90, 180);

    @Spy
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private UserServiceImpl userService;

    @InjectMocks
    private AccountServiceImpl accountService;

    private User user;
    private Account account;

    @BeforeEach
    public void setUp() {
        user = new User();
        user.setName("Alice Johnson");
        user.setEmail("alice.j@example.com");
        user.setPhoneNumber("+14155557391");
        user.setPassword(passwordEncoder.encode(CURRENT_PASSWORD));
        user.setPasswordChangedAt(LocalDateTime.now().minusDays(120));

        account = new Account();
        account.setAccountNumber(ACCOUNT_NUMBER);
        account.setUser(user);
        account.setPin(passwordEncoder.encode(CURRENT_PIN));
        account.setPinChangedAt(LocalDateTime.now().minusDays(10));
        user.setAccount(account);

        when(userRepository.findByAccountAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(user));
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(account);
        when(tokenRepository.findAllByAccount(account)).thenReturn(new Token[] { new Token(), new Token() });

        val principal = org.springframework.security.core.userdetails.User
                .withUsername(ACCOUNT_NUMBER).password("x").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void test_change_password_rotates_hash_timestamp_and_revokes_sessions() {
        val before = LocalDateTime.now();

        val response = userService.changePassword(new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD));

        assertEquals(ApiMessages.PASSWORD_CHANGE_SUCCESS.getMessage(), response.getBody());
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, user.getPassword()));
        assertTrue(!user.getPasswordChangedAt().isBefore(before));
        verify(userRepository).save(user);
        verify(tokenRepository).deleteAll(any(List.class));
    }

    @Test
    public void test_change_password_with_wrong_current_password_is_rejected() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class,
                () -> userService.changePassword(new ChangePasswordRequest("wrong", NEW_PASSWORD)));
        verify(userRepository, never()).save(any());
    }

    @Test
    public void test_change_password_to_same_password_is_rejected() {
        val ex = assertThrows(UserInvalidException.class,
                () -> userService.changePassword(new ChangePasswordRequest(CURRENT_PASSWORD, CURRENT_PASSWORD)));

        assertEquals(ApiMessages.PASSWORD_REUSE_ERROR.getMessage(), ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    public void test_change_password_to_breached_password_is_rejected() {
        val ex = assertThrows(UserInvalidException.class,
                () -> userService.changePassword(new ChangePasswordRequest(CURRENT_PASSWORD, "P@ssw0rd2024!")));

        assertEquals(ApiMessages.PASSWORD_BREACHED_ERROR.getMessage(), ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    public void test_credential_status_reports_rotation_due() {
        val body = userService.getCredentialStatus().getBody();

        assertNotNull(body);
        assertEquals(true, JsonPath.read(body, "$.passwordRotationDue"));
        assertEquals(true, JsonPath.read(body, "$.pinCreated"));
        assertEquals(false, JsonPath.read(body, "$.pinRotationDue"));
        assertNotNull(JsonPath.read(body, "$.passwordChangedAt"));
    }

    @Test
    public void test_update_pin_rotates_hash_and_timestamp() {
        val before = LocalDateTime.now();

        accountService.updatePin(ACCOUNT_NUMBER, CURRENT_PIN, CURRENT_PASSWORD, "4830");

        assertTrue(passwordEncoder.matches("4830", account.getPin()));
        assertTrue(!account.getPinChangedAt().isBefore(before));
        verify(accountRepository).save(account);
    }

    @Test
    public void test_update_pin_to_same_pin_is_rejected() {
        val ex = assertThrows(InvalidPinException.class,
                () -> accountService.updatePin(ACCOUNT_NUMBER, CURRENT_PIN, CURRENT_PASSWORD, CURRENT_PIN));

        assertEquals(ApiMessages.PIN_REUSE_ERROR.getMessage(), ex.getMessage());
        verify(accountRepository, never()).save(any());
    }

    @Test
    public void test_update_pin_to_weak_pin_is_rejected() {
        val ex = assertThrows(InvalidPinException.class,
                () -> accountService.updatePin(ACCOUNT_NUMBER, CURRENT_PIN, CURRENT_PASSWORD, "2580"));

        assertEquals(ApiMessages.PIN_TOO_WEAK_ERROR.getMessage(), ex.getMessage());
        verify(accountRepository, never()).save(any());
    }

    @Test
    public void test_create_pin_matching_phone_suffix_is_rejected() {
        account.setPin(null);

        val ex = assertThrows(InvalidPinException.class,
                () -> accountService.createPin(ACCOUNT_NUMBER, CURRENT_PASSWORD, "7391"));

        assertEquals(ApiMessages.PIN_MATCHES_PHONE_ERROR.getMessage(), ex.getMessage());
    }
}
