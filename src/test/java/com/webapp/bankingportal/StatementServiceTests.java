package com.webapp.bankingportal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.webapp.bankingportal.dto.AccountStatement;
import com.webapp.bankingportal.dto.StatementLine;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.Transaction;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.exception.NotFoundException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.service.StatementCsvRenderer;
import com.webapp.bankingportal.service.StatementPdfRenderer;
import com.webapp.bankingportal.service.StatementServiceImpl;
import com.webapp.bankingportal.type.StatementFormat;

import lombok.val;

public class StatementServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");
    private static final String ACCOUNT_NUMBER = "abc123";
    private static final String OTHER_ACCOUNT_NUMBER = "zzz999";

    private AccountRepository accountRepository;
    private TransactionRepository transactionRepository;
    private Account account;
    private Account otherAccount;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        transactionRepository = mock(TransactionRepository.class);

        val user = new User();
        user.setName("Jane Doe");
        account = new Account();
        account.setAccountNumber(ACCOUNT_NUMBER);
        account.setUser(user);
        otherAccount = new Account();
        otherAccount.setAccountNumber(OTHER_ACCOUNT_NUMBER);

        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(account);
    }

    private StatementServiceImpl service(ZoneId zone) {
        return new StatementServiceImpl(accountRepository, transactionRepository,
                new StatementCsvRenderer(), new StatementPdfRenderer(), Clock.fixed(NOW, zone));
    }

    private Transaction transaction(long id, String instant, TransactionType type, double amount,
            Account source, Account target) {
        val transaction = new Transaction();
        transaction.setId(id);
        transaction.setTransactionDate(Date.from(Instant.parse(instant)));
        transaction.setTransactionType(type);
        transaction.setAmount(amount);
        transaction.setSourceAccount(source);
        transaction.setTargetAccount(target);
        return transaction;
    }

    private List<Transaction> augustAndLaterTransactions() {
        return List.of(
                transaction(1, "2026-08-02T09:00:00Z", TransactionType.CASH_DEPOSIT, 1000, account, null),
                transaction(2, "2026-08-10T09:00:00Z", TransactionType.CASH_TRANSFER, 300, account, otherAccount),
                transaction(3, "2026-08-20T09:00:00Z", TransactionType.CASH_TRANSFER, 500, otherAccount, account),
                transaction(4, "2026-08-31T23:59:59Z", TransactionType.CASH_WITHDRAWAL, 200, account, null),
                transaction(5, "2026-09-01T00:00:00Z", TransactionType.CASH_DEPOSIT, 400, account, null),
                transaction(6, "2026-09-05T09:00:00Z", TransactionType.CASH_WITHDRAWAL, 100, account, null));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    public void test_monthly_statement_derives_balances_and_lines() {
        account.setBalance(1500);
        when(transactionRepository.findByAccountNumberOnOrAfter(eq(ACCOUNT_NUMBER), any()))
                .thenReturn(augustAndLaterTransactions());

        AccountStatement statement = service(ZoneOffset.UTC).getMonthlyStatement(ACCOUNT_NUMBER, YearMonth.of(2026, 8));

        verify(transactionRepository).findByAccountNumberOnOrAfter(
                ACCOUNT_NUMBER, Date.from(Instant.parse("2026-08-01T00:00:00Z")));
        Assertions.assertEquals("Jane Doe", statement.accountHolderName());
        Assertions.assertEquals(money("200.00"), statement.openingBalance());
        Assertions.assertEquals(money("1200.00"), statement.closingBalance());
        Assertions.assertEquals(money("1500.00"), statement.totalCredits());
        Assertions.assertEquals(money("500.00"), statement.totalDebits());

        List<StatementLine> lines = statement.lines();
        Assertions.assertEquals(4, lines.size());
        Assertions.assertEquals(List.of(1L, 2L, 3L, 4L), lines.stream().map(StatementLine::transactionId).toList());
        Assertions.assertEquals(
                List.of(money("1200.00"), money("900.00"), money("1400.00"), money("1200.00")),
                lines.stream().map(StatementLine::balance).toList());

        Assertions.assertEquals(money("1000.00"), lines.get(0).credit());
        Assertions.assertNull(lines.get(0).debit());
        Assertions.assertEquals("Transfer to " + OTHER_ACCOUNT_NUMBER, lines.get(1).description());
        Assertions.assertEquals(money("300.00"), lines.get(1).debit());
        Assertions.assertEquals("Transfer from " + OTHER_ACCOUNT_NUMBER, lines.get(2).description());
        Assertions.assertEquals(money("500.00"), lines.get(2).credit());
        Assertions.assertEquals(LocalDateTime.of(2026, 8, 31, 23, 59, 59), lines.get(3).date());
    }

    @Test
    public void test_monthly_statement_without_transactions_carries_balance() {
        account.setBalance(750);
        when(transactionRepository.findByAccountNumberOnOrAfter(eq(ACCOUNT_NUMBER), any())).thenReturn(List.of());

        val statement = service(ZoneOffset.UTC).getMonthlyStatement(ACCOUNT_NUMBER, YearMonth.of(2026, 7));

        Assertions.assertTrue(statement.lines().isEmpty());
        Assertions.assertEquals(money("750.00"), statement.openingBalance());
        Assertions.assertEquals(money("750.00"), statement.closingBalance());
        Assertions.assertEquals(money("0.00"), statement.totalCredits());
        Assertions.assertEquals(money("0.00"), statement.totalDebits());
    }

    @Test
    public void test_monthly_statement_uses_configured_time_zone_for_month_boundaries() {
        val kolkata = ZoneId.of("Asia/Kolkata");
        account.setBalance(100);
        // 2026-07-31T20:00Z is 2026-08-01 01:30 in Asia/Kolkata.
        when(transactionRepository.findByAccountNumberOnOrAfter(eq(ACCOUNT_NUMBER), any())).thenReturn(List.of(
                transaction(7, "2026-07-31T20:00:00Z", TransactionType.CASH_DEPOSIT, 100, account, null)));

        val statement = service(kolkata).getMonthlyStatement(ACCOUNT_NUMBER, YearMonth.of(2026, 8));

        verify(transactionRepository).findByAccountNumberOnOrAfter(
                ACCOUNT_NUMBER, Date.from(Instant.parse("2026-07-31T18:30:00Z")));
        Assertions.assertEquals(1, statement.lines().size());
        Assertions.assertEquals(LocalDateTime.of(2026, 8, 1, 1, 30), statement.lines().get(0).date());
        Assertions.assertEquals(money("0.00"), statement.openingBalance());
    }

    @Test
    public void test_monthly_statement_for_current_month_is_allowed() {
        account.setBalance(0);
        when(transactionRepository.findByAccountNumberOnOrAfter(eq(ACCOUNT_NUMBER), any())).thenReturn(List.of());

        Assertions.assertDoesNotThrow(
                () -> service(ZoneOffset.UTC).getMonthlyStatement(ACCOUNT_NUMBER, YearMonth.of(2026, 9)));
    }

    @Test
    public void test_monthly_statement_for_future_month_is_rejected() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> service(ZoneOffset.UTC).getMonthlyStatement(ACCOUNT_NUMBER, YearMonth.of(2026, 10)));
    }

    @Test
    public void test_monthly_statement_for_unknown_account_is_not_found() {
        Assertions.assertThrows(NotFoundException.class,
                () -> service(ZoneOffset.UTC).getMonthlyStatement("unknown", YearMonth.of(2026, 8)));
    }

    @Test
    public void test_parse_month() {
        val service = service(ZoneOffset.UTC);
        Assertions.assertEquals(YearMonth.of(2026, 8), service.parseMonth(null));
        Assertions.assertEquals(YearMonth.of(2026, 8), service.parseMonth(" "));
        Assertions.assertEquals(YearMonth.of(2025, 12), service.parseMonth("2025-12"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> service.parseMonth("2025-13"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> service.parseMonth("August"));
    }

    @Test
    public void test_generate_monthly_statement_dispatches_on_format() {
        account.setBalance(1500);
        when(transactionRepository.findByAccountNumberOnOrAfter(eq(ACCOUNT_NUMBER), any()))
                .thenReturn(augustAndLaterTransactions());
        val service = service(ZoneOffset.UTC);

        val csv = new String(service.generateMonthlyStatement(ACCOUNT_NUMBER, YearMonth.of(2026, 8), StatementFormat.CSV));
        val pdf = service.generateMonthlyStatement(ACCOUNT_NUMBER, YearMonth.of(2026, 8), StatementFormat.PDF);

        Assertions.assertTrue(csv.startsWith("Date,Transaction ID,"));
        Assertions.assertEquals("%PDF", new String(pdf, 0, 4));
    }

}
