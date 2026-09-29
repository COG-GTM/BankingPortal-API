package com.webapp.bankingportal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.Transaction;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.exception.FundTransferException;
import com.webapp.bankingportal.exception.InsufficientBalanceException;
import com.webapp.bankingportal.exception.InvalidAmountException;
import com.webapp.bankingportal.exception.InvalidPinException;
import com.webapp.bankingportal.exception.NotFoundException;
import com.webapp.bankingportal.exception.UnauthorizedException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.util.ApiMessages;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplUnitTests {

    private static final String ACCOUNT_NUMBER = "abc123";
    private static final String TARGET_ACCOUNT_NUMBER = "def456";
    private static final String PASSWORD = "Password!1";
    private static final String PIN = "1234";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private final PasswordEncoder passwordEncoder = new PrefixPasswordEncoder();

    private AccountServiceImpl accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl(accountRepository, passwordEncoder, transactionRepository);
    }

    private Account account(String accountNumber, String rawPin, double balance) {
        var user = new User();
        user.setPassword(passwordEncoder.encode(PASSWORD));

        var account = new Account();
        account.setAccountNumber(accountNumber);
        account.setUser(user);
        account.setBalance(balance);
        if (rawPin != null) {
            account.setPin(passwordEncoder.encode(rawPin));
        }
        return account;
    }

    private void givenAccount(Account account) {
        when(accountRepository.findByAccountNumber(account.getAccountNumber())).thenReturn(account);
    }

    @Test
    void createAccount_generatesUniqueNumberAndZeroBalance() {
        var user = new User();
        when(accountRepository.findByAccountNumber(anyString()))
                .thenReturn(new Account())
                .thenReturn(null);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        var account = accountService.createAccount(user);

        assertNotNull(account.getAccountNumber());
        assertEquals(6, account.getAccountNumber().length());
        assertEquals(0.0, account.getBalance());
        assertSame(user, account.getUser());
        verify(accountRepository, times(2)).findByAccountNumber(anyString());
    }

    @Test
    void isPinCreated_throwsWhenAccountMissing() {
        var ex = assertThrows(NotFoundException.class, () -> accountService.isPinCreated(ACCOUNT_NUMBER));
        assertEquals(ApiMessages.ACCOUNT_NOT_FOUND.getMessage(), ex.getMessage());
    }

    @Test
    void isPinCreated_reflectsPinPresence() {
        givenAccount(account(ACCOUNT_NUMBER, null, 0));
        assertFalse(accountService.isPinCreated(ACCOUNT_NUMBER));

        givenAccount(account(ACCOUNT_NUMBER, PIN, 0));
        assertTrue(accountService.isPinCreated(ACCOUNT_NUMBER));
    }

    @Test
    void createPin_encodesAndSavesPin() {
        var account = account(ACCOUNT_NUMBER, null, 0);
        givenAccount(account);

        accountService.createPin(ACCOUNT_NUMBER, PASSWORD, PIN);

        assertTrue(passwordEncoder.matches(PIN, account.getPin()));
        verify(accountRepository).save(account);
    }

    @Test
    void createPin_throwsWhenAccountMissing() {
        assertThrows(NotFoundException.class, () -> accountService.createPin(ACCOUNT_NUMBER, PASSWORD, PIN));
    }

    @Test
    void createPin_rejectsEmptyPassword() {
        givenAccount(account(ACCOUNT_NUMBER, null, 0));

        var ex = assertThrows(UnauthorizedException.class, () -> accountService.createPin(ACCOUNT_NUMBER, "", PIN));
        assertEquals(ApiMessages.PASSWORD_EMPTY_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    void createPin_rejectsWrongPassword() {
        givenAccount(account(ACCOUNT_NUMBER, null, 0));

        var ex = assertThrows(UnauthorizedException.class,
                () -> accountService.createPin(ACCOUNT_NUMBER, "wrong", PIN));
        assertEquals(ApiMessages.PASSWORD_INVALID_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    void createPin_rejectsWhenPinAlreadyExists() {
        givenAccount(account(ACCOUNT_NUMBER, PIN, 0));

        var ex = assertThrows(UnauthorizedException.class,
                () -> accountService.createPin(ACCOUNT_NUMBER, PASSWORD, "5678"));
        assertEquals(ApiMessages.PIN_ALREADY_EXISTS.getMessage(), ex.getMessage());
    }

    @Test
    void createPin_rejectsEmptyPin() {
        givenAccount(account(ACCOUNT_NUMBER, null, 0));

        var ex = assertThrows(InvalidPinException.class, () -> accountService.createPin(ACCOUNT_NUMBER, PASSWORD, ""));
        assertEquals(ApiMessages.PIN_EMPTY_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    void createPin_rejectsBadlyFormattedPin() {
        givenAccount(account(ACCOUNT_NUMBER, null, 0));

        var ex = assertThrows(InvalidPinException.class,
                () -> accountService.createPin(ACCOUNT_NUMBER, PASSWORD, "12a4"));
        assertEquals(ApiMessages.PIN_FORMAT_INVALID_ERROR.getMessage(), ex.getMessage());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void updatePin_replacesPin() {
        var account = account(ACCOUNT_NUMBER, PIN, 0);
        givenAccount(account);

        accountService.updatePin(ACCOUNT_NUMBER, PIN, PASSWORD, "5678");

        assertTrue(passwordEncoder.matches("5678", account.getPin()));
        verify(accountRepository).save(account);
    }

    @Test
    void updatePin_rejectsWhenPinNotCreated() {
        givenAccount(account(ACCOUNT_NUMBER, null, 0));

        var ex = assertThrows(UnauthorizedException.class,
                () -> accountService.updatePin(ACCOUNT_NUMBER, PIN, PASSWORD, "5678"));
        assertEquals(ApiMessages.PIN_NOT_CREATED.getMessage(), ex.getMessage());
    }

    @Test
    void updatePin_rejectsEmptyOldPin() {
        givenAccount(account(ACCOUNT_NUMBER, PIN, 0));

        var ex = assertThrows(UnauthorizedException.class,
                () -> accountService.updatePin(ACCOUNT_NUMBER, null, PASSWORD, "5678"));
        assertEquals(ApiMessages.PIN_EMPTY_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    void updatePin_rejectsWrongOldPin() {
        givenAccount(account(ACCOUNT_NUMBER, PIN, 0));

        var ex = assertThrows(UnauthorizedException.class,
                () -> accountService.updatePin(ACCOUNT_NUMBER, "0000", PASSWORD, "5678"));
        assertEquals(ApiMessages.PIN_INVALID_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    void updatePin_rejectsEmptyNewPin() {
        givenAccount(account(ACCOUNT_NUMBER, PIN, 0));

        var ex = assertThrows(InvalidPinException.class,
                () -> accountService.updatePin(ACCOUNT_NUMBER, PIN, PASSWORD, null));
        assertEquals(ApiMessages.PIN_EMPTY_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    void updatePin_rejectsBadlyFormattedNewPin() {
        givenAccount(account(ACCOUNT_NUMBER, PIN, 0));

        var ex = assertThrows(InvalidPinException.class,
                () -> accountService.updatePin(ACCOUNT_NUMBER, PIN, PASSWORD, "12345"));
        assertEquals(ApiMessages.PIN_FORMAT_INVALID_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    void cashDeposit_increasesBalanceAndRecordsTransaction() {
        var account = account(ACCOUNT_NUMBER, PIN, 500);
        givenAccount(account);

        accountService.cashDeposit(ACCOUNT_NUMBER, PIN, 1000);

        assertEquals(1500, account.getBalance());
        verify(accountRepository).save(account);
        var captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(TransactionType.CASH_DEPOSIT, captor.getValue().getTransactionType());
        assertEquals(1000, captor.getValue().getAmount());
        assertSame(account, captor.getValue().getSourceAccount());
        assertNotNull(captor.getValue().getTransactionDate());
    }

    @Test
    void cashDeposit_rejectsNonPositiveAmount() {
        givenAccount(account(ACCOUNT_NUMBER, PIN, 0));

        var ex = assertThrows(InvalidAmountException.class, () -> accountService.cashDeposit(ACCOUNT_NUMBER, PIN, 0));
        assertEquals(ApiMessages.AMOUNT_NEGATIVE_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    void cashDeposit_rejectsAmountNotMultipleOf100() {
        givenAccount(account(ACCOUNT_NUMBER, PIN, 0));

        var ex = assertThrows(InvalidAmountException.class,
                () -> accountService.cashDeposit(ACCOUNT_NUMBER, PIN, 150));
        assertEquals(ApiMessages.AMOUNT_NOT_MULTIPLE_OF_100_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    void cashDeposit_rejectsAmountAbove100000() {
        givenAccount(account(ACCOUNT_NUMBER, PIN, 0));

        var ex = assertThrows(InvalidAmountException.class,
                () -> accountService.cashDeposit(ACCOUNT_NUMBER, PIN, 100_100));
        assertEquals(ApiMessages.AMOUNT_EXCEED_100_000_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    void cashDeposit_rejectsWhenAccountMissing() {
        assertThrows(NotFoundException.class, () -> accountService.cashDeposit(ACCOUNT_NUMBER, PIN, 100));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void cashWithdrawal_decreasesBalanceAndRecordsTransaction() {
        var account = account(ACCOUNT_NUMBER, PIN, 1000);
        givenAccount(account);

        accountService.cashWithdrawal(ACCOUNT_NUMBER, PIN, 400);

        assertEquals(600, account.getBalance());
        var captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(TransactionType.CASH_WITHDRAWAL, captor.getValue().getTransactionType());
        assertEquals(400, captor.getValue().getAmount());
    }

    @Test
    void cashWithdrawal_rejectsInsufficientBalance() {
        var account = account(ACCOUNT_NUMBER, PIN, 100);
        givenAccount(account);

        var ex = assertThrows(InsufficientBalanceException.class,
                () -> accountService.cashWithdrawal(ACCOUNT_NUMBER, PIN, 200));
        assertEquals(ApiMessages.BALANCE_INSUFFICIENT_ERROR.getMessage(), ex.getMessage());
        assertEquals(100, account.getBalance());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void fundTransfer_movesMoneyAndRecordsTransaction() {
        var source = account(ACCOUNT_NUMBER, PIN, 1000);
        var target = account(TARGET_ACCOUNT_NUMBER, null, 200);
        givenAccount(source);
        givenAccount(target);

        accountService.fundTransfer(ACCOUNT_NUMBER, TARGET_ACCOUNT_NUMBER, PIN, 300);

        assertEquals(700, source.getBalance());
        assertEquals(500, target.getBalance());
        verify(accountRepository).save(source);
        verify(accountRepository).save(target);
        var captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(TransactionType.CASH_TRANSFER, captor.getValue().getTransactionType());
        assertSame(source, captor.getValue().getSourceAccount());
        assertSame(target, captor.getValue().getTargetAccount());
    }

    @Test
    void fundTransfer_rejectsSameAccount() {
        givenAccount(account(ACCOUNT_NUMBER, PIN, 1000));

        var ex = assertThrows(FundTransferException.class,
                () -> accountService.fundTransfer(ACCOUNT_NUMBER, ACCOUNT_NUMBER, PIN, 100));
        assertEquals(ApiMessages.CASH_TRANSFER_SAME_ACCOUNT_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    void fundTransfer_rejectsMissingTargetAccount() {
        givenAccount(account(ACCOUNT_NUMBER, PIN, 1000));

        var ex = assertThrows(NotFoundException.class,
                () -> accountService.fundTransfer(ACCOUNT_NUMBER, TARGET_ACCOUNT_NUMBER, PIN, 100));
        assertEquals(ApiMessages.ACCOUNT_NOT_FOUND.getMessage(), ex.getMessage());
    }

    @Test
    void fundTransfer_rejectsInsufficientBalance() {
        var source = account(ACCOUNT_NUMBER, PIN, 100);
        var target = account(TARGET_ACCOUNT_NUMBER, null, 0);
        givenAccount(source);
        givenAccount(target);

        assertThrows(InsufficientBalanceException.class,
                () -> accountService.fundTransfer(ACCOUNT_NUMBER, TARGET_ACCOUNT_NUMBER, PIN, 500));
        assertEquals(100, source.getBalance());
        assertEquals(0, target.getBalance());
        assertNull(target.getPin());
        verify(transactionRepository, never()).save(any());
    }

    private static final class PrefixPasswordEncoder implements PasswordEncoder {
        @Override
        public String encode(CharSequence rawPassword) {
            return "enc:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encodedPassword != null && encodedPassword.equals(encode(rawPassword));
        }
    }
}
