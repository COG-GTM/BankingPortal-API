package com.webapp.bankingportal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.webapp.bankingportal.entity.Transaction;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.exception.ForbiddenException;
import com.webapp.bankingportal.exception.FundTransferException;
import com.webapp.bankingportal.exception.InsufficientBalanceException;
import com.webapp.bankingportal.exception.NotFoundException;
import com.webapp.bankingportal.exception.TransactionAlreadyReversedException;
import com.webapp.bankingportal.exception.UnauthorizedException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.val;

public class TransferReversalServiceTests extends BaseTest {

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransactionRepository transactionRepository;

    private Transaction findTransaction(String accountNumber, TransactionType type) {
        return transactionRepository
                .findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(accountNumber, accountNumber)
                .stream()
                .filter(t -> t.getTransactionType() == type)
                .findFirst()
                .orElseThrow();
    }

    private Long transfer(String sourceAccountNumber, String pin, String targetAccountNumber, double amount) {
        accountService.fundTransfer(sourceAccountNumber, targetAccountNumber, pin, amount);
        return findTransaction(sourceAccountNumber, TransactionType.CASH_TRANSFER).getId();
    }

    private double balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).getBalance();
    }

    @Test
    public void test_reverse_fund_transfer_with_valid_data() {
        val sender = createAccountWithInitialBalance(1000);
        val recipient = createAccount();
        val transactionId = transfer(sender.get("accountNumber"), sender.get("pin"),
                recipient.get("accountNumber"), 400);

        val reversal = accountService.reverseFundTransfer(sender.get("accountNumber"), transactionId,
                sender.get("pin"));

        Assertions.assertEquals(1000, balanceOf(sender.get("accountNumber")));
        Assertions.assertEquals(0, balanceOf(recipient.get("accountNumber")));

        Assertions.assertNotNull(reversal.getId());
        Assertions.assertEquals(TransactionType.CASH_TRANSFER_REVERSAL, reversal.getTransactionType());
        Assertions.assertEquals(400, reversal.getAmount());
        Assertions.assertEquals(recipient.get("accountNumber"), reversal.getSourceAccountNumber());
        Assertions.assertEquals(sender.get("accountNumber"), reversal.getTargetAccountNumber());
        Assertions.assertEquals(transactionId, reversal.getReversedTransactionId());

        val storedReversal = transactionRepository.findById(reversal.getId()).orElseThrow();
        Assertions.assertEquals(transactionId, storedReversal.getReversedTransaction().getId());
        Assertions.assertTrue(transactionRepository.existsByReversedTransaction_Id(transactionId));
    }

    @Test
    public void test_reverse_fund_transfer_twice() {
        val sender = createAccountWithInitialBalance(1000);
        val recipient = createAccountWithInitialBalance(500);
        val transactionId = transfer(sender.get("accountNumber"), sender.get("pin"),
                recipient.get("accountNumber"), 300);

        accountService.reverseFundTransfer(sender.get("accountNumber"), transactionId, sender.get("pin"));

        val exception = Assertions.assertThrows(TransactionAlreadyReversedException.class,
                () -> accountService.reverseFundTransfer(sender.get("accountNumber"), transactionId,
                        sender.get("pin")));
        Assertions.assertEquals(ApiMessages.TRANSACTION_ALREADY_REVERSED.getMessage(), exception.getMessage());

        Assertions.assertEquals(1000, balanceOf(sender.get("accountNumber")));
        Assertions.assertEquals(500, balanceOf(recipient.get("accountNumber")));

        val reversals = transactionRepository
                .findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(
                        sender.get("accountNumber"), sender.get("accountNumber"))
                .stream()
                .filter(t -> t.getTransactionType() == TransactionType.CASH_TRANSFER_REVERSAL)
                .count();
        Assertions.assertEquals(1, reversals);
    }

    @Test
    public void test_reverse_fund_transfer_by_recipient() {
        val sender = createAccountWithInitialBalance(1000);
        val recipient = createAccountWithPin(passwordEncoder, userRepository, accountService);
        val transactionId = transfer(sender.get("accountNumber"), sender.get("pin"),
                recipient.get("accountNumber"), 400);

        Assertions.assertThrows(ForbiddenException.class,
                () -> accountService.reverseFundTransfer(recipient.get("accountNumber"), transactionId,
                        recipient.get("pin")));

        Assertions.assertEquals(600, balanceOf(sender.get("accountNumber")));
        Assertions.assertEquals(400, balanceOf(recipient.get("accountNumber")));
        Assertions.assertFalse(transactionRepository.existsByReversedTransaction_Id(transactionId));
    }

    @Test
    public void test_reverse_fund_transfer_by_unrelated_account() {
        val sender = createAccountWithInitialBalance(1000);
        val recipient = createAccount();
        val outsider = createAccountWithPin(passwordEncoder, userRepository, accountService);
        val transactionId = transfer(sender.get("accountNumber"), sender.get("pin"),
                recipient.get("accountNumber"), 400);

        val exception = Assertions.assertThrows(NotFoundException.class,
                () -> accountService.reverseFundTransfer(outsider.get("accountNumber"), transactionId,
                        outsider.get("pin")));
        Assertions.assertEquals(ApiMessages.TRANSACTION_NOT_FOUND.getMessage(), exception.getMessage());

        Assertions.assertEquals(600, balanceOf(sender.get("accountNumber")));
        Assertions.assertEquals(400, balanceOf(recipient.get("accountNumber")));
    }

    @Test
    public void test_reverse_fund_transfer_with_nonexistent_transaction() {
        val sender = createAccountWithInitialBalance(1000);

        Assertions.assertThrows(NotFoundException.class,
                () -> accountService.reverseFundTransfer(sender.get("accountNumber"), Long.MAX_VALUE,
                        sender.get("pin")));
    }

    @Test
    public void test_reverse_fund_transfer_with_invalid_pin() {
        val sender = createAccountWithInitialBalance(1000);
        val recipient = createAccount();
        val transactionId = transfer(sender.get("accountNumber"), sender.get("pin"),
                recipient.get("accountNumber"), 400);

        var wrongPin = getRandomPin();
        while (wrongPin.equals(sender.get("pin"))) {
            wrongPin = getRandomPin();
        }
        val invalidPin = wrongPin;

        Assertions.assertThrows(UnauthorizedException.class,
                () -> accountService.reverseFundTransfer(sender.get("accountNumber"), transactionId, invalidPin));
        Assertions.assertThrows(UnauthorizedException.class,
                () -> accountService.reverseFundTransfer(sender.get("accountNumber"), transactionId, null));

        Assertions.assertEquals(600, balanceOf(sender.get("accountNumber")));
        Assertions.assertFalse(transactionRepository.existsByReversedTransaction_Id(transactionId));
    }

    @Test
    public void test_reverse_non_transfer_transaction() {
        val account = createAccountWithInitialBalance(1000);
        val depositId = findTransaction(account.get("accountNumber"), TransactionType.CASH_DEPOSIT).getId();

        val exception = Assertions.assertThrows(FundTransferException.class,
                () -> accountService.reverseFundTransfer(account.get("accountNumber"), depositId,
                        account.get("pin")));
        Assertions.assertEquals(ApiMessages.TRANSACTION_NOT_REVERSIBLE.getMessage(), exception.getMessage());
        Assertions.assertEquals(1000, balanceOf(account.get("accountNumber")));
    }

    @Test
    public void test_reverse_a_reversal_transaction() {
        val sender = createAccountWithInitialBalance(1000);
        val recipient = createAccountWithPin(passwordEncoder, userRepository, accountService);
        val transactionId = transfer(sender.get("accountNumber"), sender.get("pin"),
                recipient.get("accountNumber"), 400);
        val reversal = accountService.reverseFundTransfer(sender.get("accountNumber"), transactionId,
                sender.get("pin"));

        Assertions.assertThrows(FundTransferException.class,
                () -> accountService.reverseFundTransfer(recipient.get("accountNumber"), reversal.getId(),
                        recipient.get("pin")));
        Assertions.assertThrows(FundTransferException.class,
                () -> accountService.reverseFundTransfer(sender.get("accountNumber"), reversal.getId(),
                        sender.get("pin")));

        Assertions.assertEquals(1000, balanceOf(sender.get("accountNumber")));
        Assertions.assertEquals(0, balanceOf(recipient.get("accountNumber")));
    }

    @Test
    public void test_reverse_fund_transfer_when_recipient_has_insufficient_balance() {
        val sender = createAccountWithInitialBalance(1000);
        val recipient = createAccountWithPin(passwordEncoder, userRepository, accountService);
        val transactionId = transfer(sender.get("accountNumber"), sender.get("pin"),
                recipient.get("accountNumber"), 400);
        accountService.cashWithdrawal(recipient.get("accountNumber"), recipient.get("pin"), 300);

        val exception = Assertions.assertThrows(InsufficientBalanceException.class,
                () -> accountService.reverseFundTransfer(sender.get("accountNumber"), transactionId,
                        sender.get("pin")));
        Assertions.assertEquals(ApiMessages.TRANSACTION_REVERSAL_INSUFFICIENT_BALANCE.getMessage(),
                exception.getMessage());

        Assertions.assertEquals(600, balanceOf(sender.get("accountNumber")));
        Assertions.assertEquals(100, balanceOf(recipient.get("accountNumber")));
        Assertions.assertFalse(transactionRepository.existsByReversedTransaction_Id(transactionId));
    }

}
