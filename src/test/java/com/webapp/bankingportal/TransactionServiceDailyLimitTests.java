package com.webapp.bankingportal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.webapp.bankingportal.config.TransferLimitProperties;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.exception.DailyTransferLimitExceededException;
import com.webapp.bankingportal.exception.FundTransferException;
import com.webapp.bankingportal.mapper.TransactionMapper;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.service.EmailService;
import com.webapp.bankingportal.service.TransactionServiceImpl;

import lombok.val;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceDailyLimitTests {

    private static final String ACCOUNT_NUMBER = "abc123";
    private static final double DAILY_LIMIT = 1000.0;
    private static final Instant NOW = Instant.parse("2026-09-29T10:15:30Z");

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private EmailService emailService;

    @Mock
    private AccountRepository accountRepository;

    private TransferLimitProperties properties;
    private Account account;

    @BeforeEach
    public void setUp() {
        properties = new TransferLimitProperties();
        properties.setDailyLimit(DAILY_LIMIT);
        properties.setZone(ZoneId.of("UTC"));

        account = new Account();
        account.setAccountNumber(ACCOUNT_NUMBER);
    }

    private TransactionServiceImpl serviceAt(Instant now) {
        return new TransactionServiceImpl(transactionRepository, transactionMapper, emailService,
                accountRepository, properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private void givenTransferredToday(double amount) {
        when(transactionRepository.sumAmountBySourceAccountAndTypeBetween(
                eq(ACCOUNT_NUMBER), eq(TransactionType.CASH_TRANSFER), any(Date.class), any(Date.class)))
                .thenReturn(amount);
    }

    @Test
    public void test_transfer_below_limit_is_allowed() {
        givenTransferredToday(0.0);
        Assertions.assertDoesNotThrow(() -> serviceAt(NOW).validateDailyTransferLimit(account, 500.0));
    }

    @Test
    public void test_single_transfer_exactly_at_limit_is_allowed() {
        givenTransferredToday(0.0);
        Assertions.assertDoesNotThrow(() -> serviceAt(NOW).validateDailyTransferLimit(account, DAILY_LIMIT));
    }

    @Test
    public void test_single_transfer_just_above_limit_is_rejected() {
        givenTransferredToday(0.0);
        Assertions.assertThrows(DailyTransferLimitExceededException.class,
                () -> serviceAt(NOW).validateDailyTransferLimit(account, DAILY_LIMIT + 0.01));
    }

    @Test
    public void test_cumulative_transfers_reaching_limit_exactly_are_allowed() {
        givenTransferredToday(700.0);
        Assertions.assertDoesNotThrow(() -> serviceAt(NOW).validateDailyTransferLimit(account, 300.0));
    }

    @Test
    public void test_cumulative_transfers_exceeding_limit_are_rejected() {
        givenTransferredToday(700.0);

        val ex = Assertions.assertThrows(DailyTransferLimitExceededException.class,
                () -> serviceAt(NOW).validateDailyTransferLimit(account, 400.0));

        Assertions.assertEquals(
                "Daily transfer limit of 1000.00 exceeded. Remaining limit for today: 300.00", ex.getMessage());
    }

    @Test
    public void test_any_transfer_rejected_once_limit_is_exhausted() {
        givenTransferredToday(DAILY_LIMIT);

        val ex = Assertions.assertThrows(DailyTransferLimitExceededException.class,
                () -> serviceAt(NOW).validateDailyTransferLimit(account, 100.0));

        Assertions.assertTrue(ex.getMessage().endsWith("Remaining limit for today: 0.00"));
    }

    @Test
    public void test_limit_exceeded_exception_is_a_fund_transfer_exception() {
        givenTransferredToday(DAILY_LIMIT);
        Assertions.assertThrows(FundTransferException.class,
                () -> serviceAt(NOW).validateDailyTransferLimit(account, 100.0));
    }

    @Test
    public void test_account_override_higher_than_default_is_used() {
        account.setDailyTransferLimit(5000.0);
        givenTransferredToday(2000.0);

        Assertions.assertDoesNotThrow(() -> serviceAt(NOW).validateDailyTransferLimit(account, 3000.0));
        Assertions.assertThrows(DailyTransferLimitExceededException.class,
                () -> serviceAt(NOW).validateDailyTransferLimit(account, 3000.01));
    }

    @Test
    public void test_account_override_lower_than_default_is_used() {
        account.setDailyTransferLimit(500.0);
        givenTransferredToday(0.0);

        Assertions.assertDoesNotThrow(() -> serviceAt(NOW).validateDailyTransferLimit(account, 500.0));
        Assertions.assertThrows(DailyTransferLimitExceededException.class,
                () -> serviceAt(NOW).validateDailyTransferLimit(account, 600.0));
    }

    @Test
    public void test_usage_is_summed_over_current_calendar_day() {
        givenTransferredToday(0.0);

        serviceAt(NOW).validateDailyTransferLimit(account, 100.0);

        verify(transactionRepository).sumAmountBySourceAccountAndTypeBetween(
                ACCOUNT_NUMBER,
                TransactionType.CASH_TRANSFER,
                Date.from(Instant.parse("2026-09-29T00:00:00Z")),
                Date.from(Instant.parse("2026-09-30T00:00:00Z")));
    }

    @Test
    public void test_usage_window_just_before_midnight() {
        givenTransferredToday(0.0);

        serviceAt(Instant.parse("2026-09-29T23:59:59.999Z")).validateDailyTransferLimit(account, 100.0);

        verify(transactionRepository).sumAmountBySourceAccountAndTypeBetween(
                ACCOUNT_NUMBER,
                TransactionType.CASH_TRANSFER,
                Date.from(Instant.parse("2026-09-29T00:00:00Z")),
                Date.from(Instant.parse("2026-09-30T00:00:00Z")));
    }

    @Test
    public void test_usage_window_resets_at_midnight() {
        givenTransferredToday(0.0);

        serviceAt(Instant.parse("2026-09-30T00:00:00Z")).validateDailyTransferLimit(account, 100.0);

        verify(transactionRepository).sumAmountBySourceAccountAndTypeBetween(
                ACCOUNT_NUMBER,
                TransactionType.CASH_TRANSFER,
                Date.from(Instant.parse("2026-09-30T00:00:00Z")),
                Date.from(Instant.parse("2026-10-01T00:00:00Z")));
    }

    @Test
    public void test_usage_window_respects_configured_zone() {
        properties.setZone(ZoneId.of("Asia/Kolkata"));
        when(transactionRepository.sumAmountBySourceAccountAndTypeBetween(
                anyString(), any(TransactionType.class), any(Date.class), any(Date.class)))
                .thenReturn(0.0);

        // 20:00 UTC is already 01:30 the next day in IST (UTC+05:30)
        serviceAt(Instant.parse("2026-09-29T20:00:00Z")).validateDailyTransferLimit(account, 100.0);

        verify(transactionRepository).sumAmountBySourceAccountAndTypeBetween(
                ACCOUNT_NUMBER,
                TransactionType.CASH_TRANSFER,
                Date.from(Instant.parse("2026-09-29T18:30:00Z")),
                Date.from(Instant.parse("2026-09-30T18:30:00Z")));
    }

}
