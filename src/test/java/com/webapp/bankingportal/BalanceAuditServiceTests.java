package com.webapp.bankingportal;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.webapp.bankingportal.entity.BalanceAuditLog;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.exception.InsufficientBalanceException;
import com.webapp.bankingportal.repository.BalanceAuditLogRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.service.BalanceAuditService;

import jakarta.persistence.EntityManager;
import lombok.val;

public class BalanceAuditServiceTests extends BaseTest {

    @Autowired
    BalanceAuditLogRepository balanceAuditLogRepository;

    @Autowired
    BalanceAuditService balanceAuditService;

    @Autowired
    TransactionRepository transactionRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    public void test_deposit_records_audit_entry() {
        val accountDetails = createAccountWithPin(passwordEncoder, userRepository, accountService);
        val accountNumber = accountDetails.get("accountNumber");

        accountService.cashDeposit(accountNumber, accountDetails.get("pin"), 1000);

        val entries = balanceAuditLogRepository.findByAccountNumberOrderByIdAsc(accountNumber);
        Assertions.assertEquals(1, entries.size());

        val entry = entries.get(0);
        Assertions.assertEquals(TransactionType.CASH_DEPOSIT, entry.getOperation());
        Assertions.assertEquals(1000, entry.getAmount());
        Assertions.assertEquals(0, entry.getBalanceBefore());
        Assertions.assertEquals(1000, entry.getBalanceAfter());
        Assertions.assertEquals(accountNumber, entry.getPerformedBy());
        Assertions.assertNull(entry.getCounterpartyAccountNumber());
        Assertions.assertNotNull(entry.getCreatedAt());
        Assertions.assertTrue(transactionRepository.findById(entry.getTransactionId()).isPresent());
    }

    @Test
    public void test_withdrawal_records_audit_entry() {
        val accountDetails = createAccountWithInitialBalance(1000);
        val accountNumber = accountDetails.get("accountNumber");

        accountService.cashWithdrawal(accountNumber, accountDetails.get("pin"), 300);

        val entries = balanceAuditLogRepository.findByAccountNumberOrderByIdAsc(accountNumber);
        Assertions.assertEquals(2, entries.size());

        val entry = entries.get(1);
        Assertions.assertEquals(TransactionType.CASH_WITHDRAWAL, entry.getOperation());
        Assertions.assertEquals(300, entry.getAmount());
        Assertions.assertEquals(1000, entry.getBalanceBefore());
        Assertions.assertEquals(700, entry.getBalanceAfter());
        Assertions.assertEquals(accountNumber, entry.getPerformedBy());
    }

    @Test
    public void test_fund_transfer_records_entry_for_both_accounts() {
        val source = createAccountWithInitialBalance(1000);
        val target = createAccountWithInitialBalance(500);
        val sourceAccountNumber = source.get("accountNumber");
        val targetAccountNumber = target.get("accountNumber");

        accountService.fundTransfer(sourceAccountNumber, targetAccountNumber, source.get("pin"), 400);

        val sourceEntries = balanceAuditLogRepository.findByAccountNumberOrderByIdAsc(sourceAccountNumber);
        val targetEntries = balanceAuditLogRepository.findByAccountNumberOrderByIdAsc(targetAccountNumber);
        Assertions.assertEquals(2, sourceEntries.size());
        Assertions.assertEquals(2, targetEntries.size());

        val debit = sourceEntries.get(1);
        Assertions.assertEquals(TransactionType.CASH_TRANSFER, debit.getOperation());
        Assertions.assertEquals(1000, debit.getBalanceBefore());
        Assertions.assertEquals(600, debit.getBalanceAfter());
        Assertions.assertEquals(sourceAccountNumber, debit.getPerformedBy());
        Assertions.assertEquals(targetAccountNumber, debit.getCounterpartyAccountNumber());

        val credit = targetEntries.get(1);
        Assertions.assertEquals(TransactionType.CASH_CREDIT, credit.getOperation());
        Assertions.assertEquals(500, credit.getBalanceBefore());
        Assertions.assertEquals(900, credit.getBalanceAfter());
        Assertions.assertEquals(sourceAccountNumber, credit.getPerformedBy());
        Assertions.assertEquals(sourceAccountNumber, credit.getCounterpartyAccountNumber());

        Assertions.assertEquals(debit.getTransactionId(), credit.getTransactionId());
    }

    @Test
    public void test_failed_operation_records_no_audit_entry() {
        val accountDetails = createAccountWithInitialBalance(500);
        val accountNumber = accountDetails.get("accountNumber");

        Assertions.assertThrows(InsufficientBalanceException.class,
                () -> accountService.cashWithdrawal(accountNumber, accountDetails.get("pin"), 1000));

        Assertions.assertEquals(1, balanceAuditLogRepository.findByAccountNumberOrderByIdAsc(accountNumber).size());
    }

    @Test
    public void test_audit_entry_exposes_no_setters() {
        val publicSetters = Arrays.stream(BalanceAuditLog.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().startsWith("set"))
                .toList();

        Assertions.assertTrue(publicSetters.isEmpty(), "Unexpected setters: " + publicSetters);
    }

    @Test
    public void test_audit_entry_cannot_be_deleted() {
        val accountDetails = createAccountWithInitialBalance(1000);
        val entry = balanceAuditLogRepository
                .findByAccountNumberOrderByIdAsc(accountDetails.get("accountNumber")).get(0);

        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            entityManager.remove(entry);
            entityManager.flush();
        });
    }

    @Test
    public void test_get_audit_trail_returns_newest_first_and_paginates() {
        val accountDetails = createAccountWithInitialBalance(1000);
        val accountNumber = accountDetails.get("accountNumber");
        accountService.cashWithdrawal(accountNumber, accountDetails.get("pin"), 100);
        accountService.cashDeposit(accountNumber, accountDetails.get("pin"), 200);

        val firstPage = balanceAuditService.getAuditTrail(accountNumber, null, null, 0, 2);
        Assertions.assertEquals(3, firstPage.totalElements());
        Assertions.assertEquals(2, firstPage.totalPages());
        Assertions.assertEquals(2, firstPage.entries().size());
        Assertions.assertEquals(TransactionType.CASH_DEPOSIT, firstPage.entries().get(0).operation());
        Assertions.assertEquals(1100, firstPage.entries().get(0).balanceAfter());
        Assertions.assertEquals(TransactionType.CASH_WITHDRAWAL, firstPage.entries().get(1).operation());

        val secondPage = balanceAuditService.getAuditTrail(accountNumber, null, null, 1, 2);
        Assertions.assertEquals(1, secondPage.entries().size());
        Assertions.assertEquals(0, secondPage.entries().get(0).balanceBefore());
    }

    @Test
    public void test_get_audit_trail_filters_by_date_range() {
        val accountDetails = createAccountWithInitialBalance(1000);
        val accountNumber = accountDetails.get("accountNumber");
        val future = Instant.now().plus(1, ChronoUnit.HOURS);

        val result = balanceAuditService.getAuditTrail(accountNumber, future, future.plus(1, ChronoUnit.HOURS), 0, 20);

        Assertions.assertEquals(0, result.totalElements());
    }

    @Test
    public void test_get_audit_trail_with_invalid_arguments() {
        val accountNumber = getRandomAccountNumber();
        val now = Instant.now();

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> balanceAuditService.getAuditTrail(accountNumber, null, null, -1, 20));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> balanceAuditService.getAuditTrail(accountNumber, null, null, 0, 0));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> balanceAuditService.getAuditTrail(accountNumber, null, null, 0, 101));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> balanceAuditService.getAuditTrail(accountNumber, now, now.minusSeconds(60), 0, 20));
    }

}
