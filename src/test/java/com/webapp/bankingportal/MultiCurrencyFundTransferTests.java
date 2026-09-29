package com.webapp.bankingportal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.webapp.bankingportal.dto.TransactionDTO;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.Transaction;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.exception.ExchangeRateUnavailableException;
import com.webapp.bankingportal.exchange.CurrencyConversionService;
import com.webapp.bankingportal.exchange.StubExchangeRateProvider;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.service.AccountServiceImpl;

import lombok.val;

public class MultiCurrencyFundTransferTests {

    private static final String PIN = "1234";

    private AccountRepository accountRepository;
    private TransactionRepository transactionRepository;
    private AccountServiceImpl accountService;

    @BeforeEach
    public void setUp() {
        accountRepository = mock(AccountRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        val passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService = new AccountServiceImpl(accountRepository, passwordEncoder, transactionRepository,
                new CurrencyConversionService(new StubExchangeRateProvider()));
    }

    private Account account(String accountNumber, String currency, double balance) {
        val account = new Account();
        account.setAccountNumber(accountNumber);
        account.setCurrency(currency);
        account.setBalance(balance);
        account.setPin("encoded-pin");
        when(accountRepository.findByAccountNumber(accountNumber)).thenReturn(account);
        return account;
    }

    private Transaction savedTransaction() {
        val captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    public void test_cross_currency_transfer_debits_source_and_credits_converted_amount() {
        val source = account("usd001", "USD", 1000.0);
        val target = account("eur001", "EUR", 50.0);

        accountService.fundTransfer("usd001", "eur001", PIN, 200.0);

        Assertions.assertEquals(800.0, source.getBalance(), 0.001);
        Assertions.assertEquals(234.0, target.getBalance(), 0.001);

        val transaction = savedTransaction();
        Assertions.assertEquals(TransactionType.CASH_TRANSFER, transaction.getTransactionType());
        Assertions.assertEquals(200.0, transaction.getAmount(), 0.001);
        Assertions.assertEquals("USD", transaction.getSourceCurrency());
        Assertions.assertEquals(184.0, transaction.getTargetAmount(), 0.001);
        Assertions.assertEquals("EUR", transaction.getTargetCurrency());
        Assertions.assertEquals(0, new BigDecimal("0.92").compareTo(transaction.getExchangeRate()));
        Assertions.assertSame(source, transaction.getSourceAccount());
        Assertions.assertSame(target, transaction.getTargetAccount());
    }

    @Test
    public void test_cross_currency_transfer_rounds_to_target_minor_units() {
        account("inr001", "INR", 5000.0);
        val target = account("jpy001", "JPY", 0.0);

        accountService.fundTransfer("inr001", "jpy001", PIN, 1000.0);

        Assertions.assertEquals(1803.0, target.getBalance(), 0.001);
        Assertions.assertEquals(1803.0, savedTransaction().getTargetAmount(), 0.001);
    }

    @Test
    public void test_same_currency_transfer_records_identity_conversion() {
        val source = account("usd001", "USD", 1000.0);
        val target = account("usd002", "USD", 500.0);

        accountService.fundTransfer("usd001", "usd002", PIN, 300.0);

        Assertions.assertEquals(700.0, source.getBalance(), 0.001);
        Assertions.assertEquals(800.0, target.getBalance(), 0.001);

        val transaction = savedTransaction();
        Assertions.assertEquals(300.0, transaction.getTargetAmount(), 0.001);
        Assertions.assertEquals("USD", transaction.getTargetCurrency());
        Assertions.assertEquals(0, BigDecimal.ONE.compareTo(transaction.getExchangeRate()));
    }

    @Test
    public void test_legacy_account_without_currency_is_treated_as_default() {
        account("old001", null, 1000.0);
        val target = account("eur001", "EUR", 0.0);

        accountService.fundTransfer("old001", "eur001", PIN, 100.0);

        Assertions.assertEquals(92.0, target.getBalance(), 0.001);
        Assertions.assertEquals(Account.DEFAULT_CURRENCY, savedTransaction().getSourceCurrency());
    }

    @Test
    public void test_unsupported_currency_leaves_balances_untouched() {
        val source = account("usd001", "USD", 1000.0);
        val target = account("chf001", "CHF", 0.0);

        Assertions.assertThrows(ExchangeRateUnavailableException.class,
                () -> accountService.fundTransfer("usd001", "chf001", PIN, 200.0));

        Assertions.assertEquals(1000.0, source.getBalance(), 0.001);
        Assertions.assertEquals(0.0, target.getBalance(), 0.001);
        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    public void test_create_account_with_currency() {
        val account = accountService.createAccount(new User(), "eur");
        Assertions.assertEquals("EUR", account.getCurrency());

        Assertions.assertEquals(Account.DEFAULT_CURRENCY, accountService.createAccount(new User()).getCurrency());
    }

    @Test
    public void test_create_account_with_invalid_currency() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> accountService.createAccount(new User(), "XYZ1"));
    }

    @Test
    public void test_transaction_dto_exposes_both_amounts() {
        account("usd001", "USD", 1000.0);
        account("gbp001", "GBP", 0.0);
        accountService.fundTransfer("usd001", "gbp001", PIN, 100.0);

        val dto = new TransactionDTO(savedTransaction());

        Assertions.assertEquals(100.0, dto.getAmount(), 0.001);
        Assertions.assertEquals("USD", dto.getSourceCurrency());
        Assertions.assertEquals(79.0, dto.getTargetAmount(), 0.001);
        Assertions.assertEquals("GBP", dto.getTargetCurrency());
        Assertions.assertEquals("gbp001", dto.getTargetAccountNumber());
    }
}
