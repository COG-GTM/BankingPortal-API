package com.webapp.bankingportal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.Transaction;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.mapper.TransactionMapper;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplUnitTests {

    private static final String ACCOUNT_NUMBER = "abc123";

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private AccountRepository accountRepository;

    private TransactionServiceImpl transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionServiceImpl(transactionRepository, new TransactionMapper(),
                emailService, accountRepository);
    }

    private static Account account(String accountNumber) {
        var account = new Account();
        account.setAccountNumber(accountNumber);
        return account;
    }

    private static Transaction transaction(long id, TransactionType type, double amount, long timestamp,
            Account target) {
        var transaction = new Transaction();
        transaction.setId(id);
        transaction.setTransactionType(type);
        transaction.setAmount(amount);
        transaction.setTransactionDate(new Date(timestamp));
        transaction.setSourceAccount(account(ACCOUNT_NUMBER));
        transaction.setTargetAccount(target);
        return transaction;
    }

    private void givenTransactions(Transaction... transactions) {
        when(transactionRepository.findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(ACCOUNT_NUMBER,
                ACCOUNT_NUMBER)).thenReturn(List.of(transactions));
    }

    @Test
    void getAllTransactionsByAccountNumber_returnsDtosNewestFirst() {
        givenTransactions(
                transaction(1, TransactionType.CASH_DEPOSIT, 100, 1_000, null),
                transaction(2, TransactionType.CASH_TRANSFER, 200, 3_000, account("def456")),
                transaction(3, TransactionType.CASH_WITHDRAWAL, 300, 2_000, null));

        var result = transactionService.getAllTransactionsByAccountNumber(ACCOUNT_NUMBER);

        assertEquals(List.of(2L, 3L, 1L), result.stream().map(t -> t.getId()).toList());
        assertEquals("def456", result.get(0).getTargetAccountNumber());
        assertEquals("N/A", result.get(1).getTargetAccountNumber());
        assertEquals(ACCOUNT_NUMBER, result.get(2).getSourceAccountNumber());
    }

    @Test
    void sendBankStatementByEmail_rejectsBlankAccountNumber() {
        assertThrows(IllegalArgumentException.class, () -> transactionService.sendBankStatementByEmail(null));
        assertThrows(IllegalArgumentException.class, () -> transactionService.sendBankStatementByEmail("  "));
        verifyNoInteractions(transactionRepository, emailService);
    }

    @Test
    void sendBankStatementByEmail_emailsStatementToAccountOwner() {
        givenTransactions(transaction(1, TransactionType.CASH_DEPOSIT, 100, 1_000, null));
        var user = new User();
        user.setEmail("jane@example.com");
        var account = account(ACCOUNT_NUMBER);
        account.setUser(user);
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(account);

        transactionService.sendBankStatementByEmail(ACCOUNT_NUMBER);

        var body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(eq("jane@example.com"), eq("Your Bank Statement"), body.capture());
        assertTrue(body.getValue().startsWith("Bank Statement for Account: " + ACCOUNT_NUMBER));
        assertTrue(body.getValue().contains("Type: CASH_DEPOSIT, Amount: 100.0"));
    }

    @Test
    void sendBankStatementByEmail_skipsEmailWhenAccountMissing() {
        givenTransactions();

        transactionService.sendBankStatementByEmail(ACCOUNT_NUMBER);

        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void sendBankStatementByEmail_skipsEmailWhenAccountHasNoUser() {
        givenTransactions();
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(account(ACCOUNT_NUMBER));

        transactionService.sendBankStatementByEmail(ACCOUNT_NUMBER);

        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }
}
