package com.webapp.bankingportal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webapp.bankingportal.dto.AccountStatement;
import com.webapp.bankingportal.dto.StatementLine;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.Transaction;
import com.webapp.bankingportal.exception.NotFoundException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.type.StatementFormat;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.RequiredArgsConstructor;
import lombok.val;

@Service
@RequiredArgsConstructor
public class StatementServiceImpl implements StatementService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final StatementCsvRenderer csvRenderer;
    private final StatementPdfRenderer pdfRenderer;
    private final Clock statementClock;

    @Override
    public YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(statementClock).minusMonths(1);
        }

        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    String.format(ApiMessages.STATEMENT_MONTH_INVALID_ERROR.getMessage(), month));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AccountStatement getMonthlyStatement(String accountNumber, YearMonth month) {
        if (month.isAfter(YearMonth.now(statementClock))) {
            throw new IllegalArgumentException(ApiMessages.STATEMENT_MONTH_IN_FUTURE_ERROR.getMessage());
        }

        val account = accountRepository.findByAccountNumber(accountNumber);
        if (account == null) {
            throw new NotFoundException(ApiMessages.ACCOUNT_NOT_FOUND.getMessage());
        }

        val zone = statementClock.getZone();
        val periodStart = month.atDay(1).atStartOfDay(zone).toInstant();
        val periodEnd = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();

        // The account only stores its current balance, so historical balances are
        // derived by unwinding every transaction since the start of the period.
        val transactions = transactionRepository.findByAccountNumberOnOrAfter(
                accountNumber, Date.from(periodStart));

        val inPeriod = new ArrayList<Transaction>();
        var netAfterPeriod = BigDecimal.ZERO;
        for (Transaction transaction : transactions) {
            if (transaction.getTransactionDate().toInstant().isBefore(periodEnd)) {
                inPeriod.add(transaction);
            } else {
                netAfterPeriod = netAfterPeriod.add(signedAmount(transaction, accountNumber));
            }
        }

        val closingBalance = money(account.getBalance()).subtract(netAfterPeriod);
        val netInPeriod = inPeriod.stream()
                .map(t -> signedAmount(t, accountNumber))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        val openingBalance = closingBalance.subtract(netInPeriod);

        val lines = new ArrayList<StatementLine>();
        var runningBalance = openingBalance;
        var totalCredits = money(0);
        var totalDebits = money(0);
        for (Transaction transaction : inPeriod) {
            val signed = signedAmount(transaction, accountNumber);
            runningBalance = runningBalance.add(signed);
            val isCredit = signed.signum() >= 0;
            if (isCredit) {
                totalCredits = totalCredits.add(signed);
            } else {
                totalDebits = totalDebits.add(signed.negate());
            }

            lines.add(new StatementLine(
                    transaction.getId(),
                    LocalDateTime.ofInstant(transaction.getTransactionDate().toInstant(), zone),
                    transaction.getTransactionType(),
                    describe(transaction, accountNumber),
                    isCredit ? null : signed.negate(),
                    isCredit ? signed : null,
                    runningBalance));
        }

        return new AccountStatement(
                accountNumber,
                holderName(account),
                month,
                month.atDay(1),
                month.atEndOfMonth(),
                zone,
                openingBalance,
                closingBalance,
                totalCredits,
                totalDebits,
                lines,
                LocalDateTime.now(statementClock));
    }

    @Override
    public byte[] generateMonthlyStatement(String accountNumber, YearMonth month, StatementFormat format) {
        val statement = getMonthlyStatement(accountNumber, month);
        return switch (format) {
            case CSV -> csvRenderer.render(statement);
            case PDF -> pdfRenderer.render(statement);
        };
    }

    private static BigDecimal signedAmount(Transaction transaction, String accountNumber) {
        val amount = money(transaction.getAmount());
        return switch (transaction.getTransactionType()) {
            case CASH_DEPOSIT, CASH_CREDIT -> amount;
            case CASH_WITHDRAWAL -> amount.negate();
            case CASH_TRANSFER -> isSource(transaction, accountNumber) ? amount.negate() : amount;
        };
    }

    private static String describe(Transaction transaction, String accountNumber) {
        return switch (transaction.getTransactionType()) {
            case CASH_DEPOSIT -> "Cash deposit";
            case CASH_WITHDRAWAL -> "Cash withdrawal";
            case CASH_CREDIT -> "Credit";
            case CASH_TRANSFER -> isSource(transaction, accountNumber)
                    ? "Transfer to " + accountNumberOf(transaction.getTargetAccount())
                    : "Transfer from " + accountNumberOf(transaction.getSourceAccount());
        };
    }

    private static boolean isSource(Transaction transaction, String accountNumber) {
        return transaction.getSourceAccount() != null
                && accountNumber.equals(transaction.getSourceAccount().getAccountNumber());
    }

    private static String accountNumberOf(Account account) {
        return account == null ? "N/A" : account.getAccountNumber();
    }

    private static String holderName(Account account) {
        return account.getUser() == null ? "" : account.getUser().getName();
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

}
