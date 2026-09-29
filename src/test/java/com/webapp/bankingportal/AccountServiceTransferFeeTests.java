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
import com.webapp.bankingportal.exception.InsufficientBalanceException;
import com.webapp.bankingportal.fee.TransferFeeStrategy;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.service.AccountServiceImpl;

import lombok.val;

@ExtendWith(MockitoExtension.class)
class AccountServiceTransferFeeTests {

    private static final String SOURCE = "SRC001";
    private static final String TARGET = "TGT001";
    private static final String PIN = "1234";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransferFeeStrategy transferFeeStrategy;

    private AccountServiceImpl accountService;
    private Account source;
    private Account target;

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl(accountRepository, passwordEncoder, transactionRepository,
                transferFeeStrategy);

        source = account(SOURCE, 1000.0);
        source.setPin("encoded-pin");
        target = account(TARGET, 500.0);

        when(accountRepository.findByAccountNumber(SOURCE)).thenReturn(source);
        when(accountRepository.findByAccountNumber(TARGET)).thenReturn(target);
        when(passwordEncoder.matches(PIN, "encoded-pin")).thenReturn(true);
    }

    private static Account account(String number, double balance) {
        val account = new Account();
        account.setAccountNumber(number);
        account.setBalance(balance);
        return account;
    }

    @Test
    void deductsFeeFromSourceAndRecordsItOnTransaction() {
        when(transferFeeStrategy.calculateFee(SOURCE, 200.0)).thenReturn(5.0);

        accountService.fundTransfer(SOURCE, TARGET, PIN, 200.0);

        assertEquals(795.0, source.getBalance());
        assertEquals(700.0, target.getBalance());

        val captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(200.0, captor.getValue().getAmount());
        assertEquals(5.0, captor.getValue().getFee());
    }

    @Test
    void rejectsTransferWhenBalanceDoesNotCoverAmountPlusFee() {
        when(transferFeeStrategy.calculateFee(SOURCE, 1000.0)).thenReturn(10.0);

        assertThrows(InsufficientBalanceException.class,
                () -> accountService.fundTransfer(SOURCE, TARGET, PIN, 1000.0));

        assertEquals(1000.0, source.getBalance());
        assertEquals(500.0, target.getBalance());
        verify(transactionRepository, never()).save(any());
    }

}
