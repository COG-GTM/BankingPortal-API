package com.webapp.bankingportal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.webapp.bankingportal.dto.VelocityViolation;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.Transaction;
import com.webapp.bankingportal.entity.VelocityRule;
import com.webapp.bankingportal.exception.InsufficientBalanceException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.service.AccountServiceImpl;
import com.webapp.bankingportal.service.TransactionVelocityService;

import lombok.val;

@ExtendWith(MockitoExtension.class)
public class AccountServiceVelocityTests {

    private static final String SOURCE = "src001";
    private static final String TARGET = "tgt001";
    private static final String PIN = "1234";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionVelocityService transactionVelocityService;

    @InjectMocks
    private AccountServiceImpl accountService;

    private Account source;
    private Account target;

    @BeforeEach
    void setUp() {
        source = new Account();
        source.setAccountNumber(SOURCE);
        source.setPin("encoded");
        source.setBalance(100_000);
        target = new Account();
        target.setAccountNumber(TARGET);
        target.setBalance(0);

        when(accountRepository.findByAccountNumber(SOURCE)).thenReturn(source);
        when(passwordEncoder.matches(PIN, "encoded")).thenReturn(true);
    }

    @Test
    public void test_flagged_transfer_still_completes_and_is_sent_for_review() {
        when(accountRepository.findByAccountNumber(TARGET)).thenReturn(target);
        val violations = List.of(new VelocityViolation(VelocityRule.SINGLE_TRANSFER_AMOUNT, "big"));
        when(transactionVelocityService.evaluateTransfer(SOURCE, 60_000)).thenReturn(violations);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.fundTransfer(SOURCE, TARGET, PIN, 60_000);

        Assertions.assertEquals(40_000, source.getBalance());
        Assertions.assertEquals(60_000, target.getBalance());
        val order = inOrder(transactionVelocityService, transactionRepository);
        order.verify(transactionVelocityService).evaluateTransfer(SOURCE, 60_000);
        order.verify(transactionRepository).save(any(Transaction.class));
        order.verify(transactionVelocityService).flagForReview(
                argThat(t -> t.getAmount() == 60_000
                        && t.getSourceAccount() == source && t.getTargetAccount() == target),
                eq(violations));
    }

    @Test
    public void test_rejected_transfer_is_not_evaluated() {
        when(accountRepository.findByAccountNumber(TARGET)).thenReturn(target);
        source.setBalance(100);

        Assertions.assertThrows(InsufficientBalanceException.class,
                () -> accountService.fundTransfer(SOURCE, TARGET, PIN, 60_000));

        verify(transactionVelocityService, never()).evaluateTransfer(any(), anyDouble());
        verify(transactionVelocityService, never()).flagForReview(any(), any());
    }

}
