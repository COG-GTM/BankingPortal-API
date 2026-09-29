package com.webapp.bankingportal;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.webapp.bankingportal.dto.ScheduledTransferRequest;
import com.webapp.bankingportal.entity.ScheduledTransferStatus;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.exception.ScheduledTransferException;
import com.webapp.bankingportal.exception.UnauthorizedException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.ScheduledTransferRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.service.ScheduledTransferService;

import lombok.val;

/**
 * Runs without a surrounding test transaction because each scheduled transfer
 * is executed in its own REQUIRES_NEW transaction, which cannot see
 * uncommitted test data.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ScheduledTransferServiceTests extends BaseTest {

    @Autowired
    ScheduledTransferService scheduledTransferService;

    @Autowired
    ScheduledTransferRepository scheduledTransferRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransactionRepository transactionRepository;

    @AfterEach
    public void cleanUp() {
        scheduledTransferRepository.deleteAll();
    }

    private ScheduledTransferRequest request(String target, double amount, String pin) {
        return new ScheduledTransferRequest(target, amount, LocalDateTime.now().plusMinutes(5), pin);
    }

    @Test
    public void test_execute_due_transfer_moves_funds_and_marks_completed() {
        val source = createAccountWithInitialBalance(1000.0);
        val target = createAccountWithInitialBalance(500.0);

        val scheduled = scheduledTransferService.scheduleTransfer(source.get("accountNumber"),
                request(target.get("accountNumber"), 300.0, source.get("pin")));
        Assertions.assertEquals(ScheduledTransferStatus.PENDING, scheduled.getStatus());

        val completed = scheduledTransferService.executeDueTransfers(scheduled.getScheduledAt().plusSeconds(1));

        Assertions.assertEquals(1, completed);
        val executed = scheduledTransferRepository.findById(scheduled.getId()).orElseThrow();
        Assertions.assertEquals(ScheduledTransferStatus.COMPLETED, executed.getStatus());
        Assertions.assertNotNull(executed.getExecutedAt());
        Assertions.assertNull(executed.getFailureReason());

        Assertions.assertEquals(700.0,
                accountRepository.findByAccountNumber(source.get("accountNumber")).getBalance(), 0.01);
        Assertions.assertEquals(800.0,
                accountRepository.findByAccountNumber(target.get("accountNumber")).getBalance(), 0.01);

        val transferTransactions = transactionRepository
                .findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(
                        source.get("accountNumber"), source.get("accountNumber"))
                .stream()
                .filter(t -> t.getTransactionType() == TransactionType.CASH_TRANSFER)
                .toList();
        Assertions.assertEquals(1, transferTransactions.size());
        Assertions.assertEquals(300.0, transferTransactions.get(0).getAmount(), 0.01);
    }

    @Test
    public void test_execute_due_transfer_with_insufficient_funds_marks_failed() {
        val source = createAccountWithInitialBalance(500.0);
        val target = createAccountWithInitialBalance(500.0);

        val scheduled = scheduledTransferService.scheduleTransfer(source.get("accountNumber"),
                request(target.get("accountNumber"), 1000.0, source.get("pin")));

        val completed = scheduledTransferService.executeDueTransfers(scheduled.getScheduledAt().plusSeconds(1));

        Assertions.assertEquals(0, completed);
        val failed = scheduledTransferRepository.findById(scheduled.getId()).orElseThrow();
        Assertions.assertEquals(ScheduledTransferStatus.FAILED, failed.getStatus());
        Assertions.assertEquals("Insufficient balance", failed.getFailureReason());

        Assertions.assertEquals(500.0,
                accountRepository.findByAccountNumber(source.get("accountNumber")).getBalance(), 0.01);
        Assertions.assertEquals(500.0,
                accountRepository.findByAccountNumber(target.get("accountNumber")).getBalance(), 0.01);

        Assertions.assertEquals(0, scheduledTransferService.executeDueTransfers(
                scheduled.getScheduledAt().plusMinutes(10)));
        Assertions.assertEquals(ScheduledTransferStatus.FAILED,
                scheduledTransferRepository.findById(scheduled.getId()).orElseThrow().getStatus());
    }

    @Test
    public void test_failed_transfer_does_not_block_other_due_transfers() {
        val poor = createAccountWithInitialBalance(100.0);
        val rich = createAccountWithInitialBalance(1000.0);
        val target = createAccount();

        val failing = scheduledTransferService.scheduleTransfer(poor.get("accountNumber"),
                request(target.get("accountNumber"), 500.0, poor.get("pin")));
        val succeeding = scheduledTransferService.scheduleTransfer(rich.get("accountNumber"),
                request(target.get("accountNumber"), 500.0, rich.get("pin")));

        val completed = scheduledTransferService.executeDueTransfers(LocalDateTime.now().plusHours(1));

        Assertions.assertEquals(1, completed);
        Assertions.assertEquals(ScheduledTransferStatus.FAILED,
                scheduledTransferRepository.findById(failing.getId()).orElseThrow().getStatus());
        Assertions.assertEquals(ScheduledTransferStatus.COMPLETED,
                scheduledTransferRepository.findById(succeeding.getId()).orElseThrow().getStatus());
        Assertions.assertEquals(500.0,
                accountRepository.findByAccountNumber(target.get("accountNumber")).getBalance(), 0.01);
    }

    @Test
    public void test_transfer_not_yet_due_is_not_executed() {
        val source = createAccountWithInitialBalance(1000.0);
        val target = createAccount();

        val scheduled = scheduledTransferService.scheduleTransfer(source.get("accountNumber"),
                request(target.get("accountNumber"), 300.0, source.get("pin")));

        Assertions.assertEquals(0, scheduledTransferService.executeDueTransfers(
                scheduled.getScheduledAt().minusSeconds(1)));
        Assertions.assertEquals(ScheduledTransferStatus.PENDING,
                scheduledTransferRepository.findById(scheduled.getId()).orElseThrow().getStatus());
    }

    @Test
    public void test_cancelled_transfer_is_not_executed() {
        val source = createAccountWithInitialBalance(1000.0);
        val target = createAccount();

        val scheduled = scheduledTransferService.scheduleTransfer(source.get("accountNumber"),
                request(target.get("accountNumber"), 300.0, source.get("pin")));
        scheduledTransferService.cancelScheduledTransfer(source.get("accountNumber"), scheduled.getId());

        Assertions.assertEquals(0, scheduledTransferService.executeDueTransfers(
                scheduled.getScheduledAt().plusSeconds(1)));
        Assertions.assertEquals(ScheduledTransferStatus.CANCELLED,
                scheduledTransferRepository.findById(scheduled.getId()).orElseThrow().getStatus());
        Assertions.assertEquals(1000.0,
                accountRepository.findByAccountNumber(source.get("accountNumber")).getBalance(), 0.01);
    }

    @Test
    public void test_schedule_transfer_with_past_date() {
        val source = createAccountWithInitialBalance(1000.0);
        val target = createAccount();

        Assertions.assertThrows(ScheduledTransferException.class, () -> scheduledTransferService.scheduleTransfer(
                source.get("accountNumber"),
                new ScheduledTransferRequest(target.get("accountNumber"), 100.0,
                        LocalDateTime.now().minusMinutes(1), source.get("pin"))));
    }

    @Test
    public void test_schedule_transfer_with_invalid_pin() {
        val source = createAccountWithInitialBalance(1000.0);
        val target = createAccount();
        val wrongPin = String.format("%04d", (Integer.parseInt(source.get("pin")) + 1) % 10000);

        Assertions.assertThrows(UnauthorizedException.class, () -> scheduledTransferService.scheduleTransfer(
                source.get("accountNumber"),
                request(target.get("accountNumber"), 100.0, wrongPin)));
    }

}
