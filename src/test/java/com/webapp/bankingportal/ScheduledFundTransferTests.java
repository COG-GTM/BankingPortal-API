package com.webapp.bankingportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import com.webapp.bankingportal.exception.FundTransferException;
import com.webapp.bankingportal.exception.InsufficientBalanceException;
import com.webapp.bankingportal.exception.NotFoundException;
import com.webapp.bankingportal.exception.UnauthorizedException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.service.AccountServiceImpl;

@ExtendWith(MockitoExtension.class)
public class ScheduledFundTransferTests {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TransactionRepository transactionRepository;

    private AccountServiceImpl accountService;

    private Account source;
    private Account target;

    @BeforeEach
    public void setUp() {
        accountService = new AccountServiceImpl(accountRepository, passwordEncoder, transactionRepository);
        source = new Account();
        source.setAccountNumber("src001");
        source.setBalance(1000);
        source.setPin("encoded");
        target = new Account();
        target.setAccountNumber("tgt001");
        target.setBalance(0);
    }

    @Test
    public void test_scheduled_transfer_moves_funds_without_pin() {
        when(accountRepository.findByAccountNumber("src001")).thenReturn(source);
        when(accountRepository.findByAccountNumber("tgt001")).thenReturn(target);

        accountService.scheduledFundTransfer("src001", "tgt001", 300);

        assertEquals(700, source.getBalance());
        assertEquals(300, target.getBalance());
        var captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(TransactionType.CASH_TRANSFER, captor.getValue().getTransactionType());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    public void test_scheduled_transfer_with_insufficient_balance() {
        when(accountRepository.findByAccountNumber("src001")).thenReturn(source);
        when(accountRepository.findByAccountNumber("tgt001")).thenReturn(target);

        assertThrows(InsufficientBalanceException.class,
                () -> accountService.scheduledFundTransfer("src001", "tgt001", 2000));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    public void test_scheduled_transfer_with_missing_accounts() {
        assertThrows(NotFoundException.class,
                () -> accountService.scheduledFundTransfer("src001", "tgt001", 300));

        when(accountRepository.findByAccountNumber("src001")).thenReturn(source);
        assertThrows(NotFoundException.class,
                () -> accountService.scheduledFundTransfer("src001", "tgt001", 300));
    }

    @Test
    public void test_validate_fund_transfer_checks_pin_and_target() {
        when(accountRepository.findByAccountNumber("src001")).thenReturn(source);
        when(accountRepository.findByAccountNumber("tgt001")).thenReturn(target);
        when(passwordEncoder.matches("1234", "encoded")).thenReturn(true);
        when(passwordEncoder.matches("9999", "encoded")).thenReturn(false);

        accountService.validateFundTransfer("src001", "tgt001", "1234", 300);

        assertThrows(UnauthorizedException.class,
                () -> accountService.validateFundTransfer("src001", "tgt001", "9999", 300));
        assertThrows(FundTransferException.class,
                () -> accountService.validateFundTransfer("src001", "src001", "1234", 300));
        assertEquals(1000, source.getBalance());
        verify(transactionRepository, never()).save(any());
    }

}
