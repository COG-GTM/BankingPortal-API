package com.webapp.bankingportal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.exception.TransactionAlreadyReversedException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;

import lombok.val;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class TransferReversalConcurrencyTests extends BaseTest {

    private static final int CONCURRENT_REQUESTS = 4;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransactionRepository transactionRepository;

    @RepeatedTest(3)
    public void test_concurrent_reversals_of_same_transfer_reverse_it_once() throws Exception {
        val sender = createAccountWithInitialBalance(1000);
        val recipient = createAccount();
        val senderAccountNumber = sender.get("accountNumber");
        accountService.fundTransfer(senderAccountNumber, recipient.get("accountNumber"), sender.get("pin"), 400);
        val transactionId = transactionRepository
                .findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(senderAccountNumber,
                        senderAccountNumber)
                .stream()
                .filter(t -> t.getTransactionType() == TransactionType.CASH_TRANSFER)
                .findFirst()
                .orElseThrow()
                .getId();

        val executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        val start = new CountDownLatch(1);
        val futures = new ArrayList<Future<Object>>();
        try {
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                Callable<Object> task = () -> {
                    start.await();
                    return accountService.reverseFundTransfer(senderAccountNumber, transactionId, sender.get("pin"));
                };
                futures.add(executor.submit(task));
            }
            start.countDown();

            int successes = 0;
            List<Throwable> unexpected = new ArrayList<>();
            int conflicts = 0;
            for (val future : futures) {
                try {
                    future.get(60, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof TransactionAlreadyReversedException) {
                        conflicts++;
                    } else {
                        unexpected.add(e.getCause());
                    }
                }
            }

            Assertions.assertTrue(unexpected.isEmpty(), () -> "Unexpected failures: " + unexpected);
            Assertions.assertEquals(1, successes);
            Assertions.assertEquals(CONCURRENT_REQUESTS - 1, conflicts);
        } finally {
            executor.shutdownNow();
        }

        Assertions.assertEquals(1000, accountRepository.findByAccountNumber(senderAccountNumber).getBalance());
        Assertions.assertEquals(0,
                accountRepository.findByAccountNumber(recipient.get("accountNumber")).getBalance());
        val reversals = transactionRepository
                .findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(senderAccountNumber,
                        senderAccountNumber)
                .stream()
                .filter(t -> t.getTransactionType() == TransactionType.CASH_TRANSFER_REVERSAL)
                .count();
        Assertions.assertEquals(1, reversals);
    }

}
