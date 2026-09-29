package com.webapp.bankingportal.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.exception.InvalidPinException;
import com.webapp.bankingportal.exception.NotFoundException;
import com.webapp.bankingportal.exception.UnauthorizedException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.service.AccountServiceImpl;

import lombok.val;

class AccountServiceAuditLoggingTest {

    private static final long USER_ID = 42L;
    private static final String EMAIL = "jane.doe@example.com";
    private static final String NAME = "Jane Doe";
    private static final String ACCOUNT_NUMBER = "a7f3c9";
    private static final String PASSWORD = "Sup3r-Secret!Pass";
    private static final String PASSWORD_HASH = "$2a$10$passwordHashValue";
    private static final String OLD_PIN = "8531";
    private static final String OLD_PIN_HASH = "$2a$10$oldPinHashValue";
    private static final String NEW_PIN = "9642";
    private static final String NEW_PIN_HASH = "$2a$10$newPinHashValue";

    private static final String[] SENSITIVE = {
            EMAIL, NAME, ACCOUNT_NUMBER, PASSWORD, PASSWORD_HASH, OLD_PIN, OLD_PIN_HASH, NEW_PIN, NEW_PIN_HASH };

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private AccountServiceImpl accountService;
    private Account account;
    private LogCapture logs;

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl(
                accountRepository, passwordEncoder, mock(TransactionRepository.class), new SecurityAuditLogger());

        val user = new User();
        user.setId(USER_ID);
        user.setName(NAME);
        user.setEmail(EMAIL);
        user.setPassword(PASSWORD_HASH);
        account = new Account();
        account.setAccountNumber(ACCOUNT_NUMBER);
        account.setUser(user);
        account.setPin(OLD_PIN_HASH);

        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(account);
        when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(passwordEncoder.matches(OLD_PIN, OLD_PIN_HASH)).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn(NEW_PIN_HASH);

        logs = new LogCapture();
    }

    @AfterEach
    void tearDown() {
        logs.close();
    }

    @Test
    void updatePinSuccess_isAuditedWithoutSensitiveData() {
        accountService.updatePin(ACCOUNT_NUMBER, OLD_PIN, PASSWORD, NEW_PIN);

        assertThat(LogCapture.keyValues(logs.singleAuditEvent()))
                .containsEntry("event", "PIN_CHANGE_SUCCESS")
                .containsEntry("pin_operation", "UPDATE")
                .containsEntry("user_id", String.valueOf(USER_ID));
        logs.assertNoneContain(SENSITIVE);
    }

    @Test
    void updatePinWithWrongOldPin_isAuditedWithoutSensitiveData() {
        when(passwordEncoder.matches(OLD_PIN, OLD_PIN_HASH)).thenReturn(false);

        assertThatThrownBy(() -> accountService.updatePin(ACCOUNT_NUMBER, OLD_PIN, PASSWORD, NEW_PIN))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(LogCapture.keyValues(logs.singleAuditEvent()))
                .containsEntry("event", "PIN_CHANGE_FAILURE")
                .containsEntry("outcome", "FAILURE")
                .containsEntry("reason", "VERIFICATION_FAILED")
                .containsEntry("user_id", String.valueOf(USER_ID));
        logs.assertNoneContain(SENSITIVE);
    }

    @Test
    void updatePinWithMalformedNewPin_isAuditedWithoutEchoingPin() {
        val malformedPin = "12ab";

        assertThatThrownBy(() -> accountService.updatePin(ACCOUNT_NUMBER, OLD_PIN, PASSWORD, malformedPin))
                .isInstanceOf(InvalidPinException.class);

        assertThat(LogCapture.keyValues(logs.singleAuditEvent()))
                .containsEntry("reason", "INVALID_NEW_PIN");
        logs.assertNoneContain(SENSITIVE);
        logs.assertNoneContain(malformedPin);
    }

    @Test
    void createPinSuccess_isAuditedWithoutSensitiveData() {
        account.setPin(null);

        accountService.createPin(ACCOUNT_NUMBER, PASSWORD, NEW_PIN);

        assertThat(LogCapture.keyValues(logs.singleAuditEvent()))
                .containsEntry("event", "PIN_CHANGE_SUCCESS")
                .containsEntry("pin_operation", "CREATE");
        logs.assertNoneContain(SENSITIVE);
    }

    @Test
    void pinChangeForUnknownAccount_isAuditedWithoutAccountNumber() {
        val unknownAccountNumber = "zz9999";

        assertThatThrownBy(() -> accountService.createPin(unknownAccountNumber, PASSWORD, NEW_PIN))
                .isInstanceOf(NotFoundException.class);

        assertThat(LogCapture.keyValues(logs.singleAuditEvent()))
                .containsEntry("event", "PIN_CHANGE_FAILURE")
                .containsEntry("reason", "ACCOUNT_NOT_FOUND")
                .doesNotContainKey("user_id");
        logs.assertNoneContain(SENSITIVE);
        logs.assertNoneContain(unknownAccountNumber);
    }

}
