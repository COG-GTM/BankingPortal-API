package com.webapp.bankingportal;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.webapp.bankingportal.exception.InsufficientBalanceException;
import com.webapp.bankingportal.repository.AccountRepository;

import lombok.val;

/**
 * Runs balance-changing operations from many threads at once, each in its own
 * committed transaction, to check that concurrent requests cannot overdraw an
 * account or lose updates.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class AccountConcurrencyTests extends BaseTest {

    private static final int THREADS = 10;

    @Autowired
    private AccountRepository accountRepository;

    private int runConcurrently(Runnable operation) throws Exception {
        val executor = Executors.newFixedThreadPool(THREADS);
        try {
            val ready = new CountDownLatch(THREADS);
            val start = new CountDownLatch(1);
            val futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(executor.submit((Callable<Boolean>) () -> {
                    ready.countDown();
                    start.await();
                    try {
                        operation.run();
                        return true;
                    } catch (InsufficientBalanceException e) {
                        return false;
                    }
                }));
            }
            ready.await();
            start.countDown();

            int successes = 0;
            for (val future : futures) {
                try {
                    if (future.get(60, TimeUnit.SECONDS)) {
                        successes++;
                    }
                } catch (ExecutionException e) {
                    Assertions.fail("Unexpected failure: " + e.getCause(), e.getCause());
                }
            }
            return successes;
        } finally {
            executor.shutdownNow();
        }
    }

    private double balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).getBalance();
    }

    @Test
    public void test_concurrent_withdrawals_cannot_overdraw() throws Exception {
        val account = createAccountWithInitialBalance(1000);
        val accountNumber = account.get("accountNumber");

        val successes = runConcurrently(
                () -> accountService.cashWithdrawal(accountNumber, account.get("pin"), 1000));

        Assertions.assertEquals(1, successes);
        Assertions.assertEquals(0.0, balanceOf(accountNumber));
    }

    @Test
    public void test_concurrent_fund_transfers_cannot_overdraw() throws Exception {
        val source = createAccountWithInitialBalance(1000);
        val target = createAccount();

        val successes = runConcurrently(() -> accountService.fundTransfer(
                source.get("accountNumber"), target.get("accountNumber"), source.get("pin"), 1000));

        Assertions.assertEquals(1, successes);
        Assertions.assertEquals(0.0, balanceOf(source.get("accountNumber")));
        Assertions.assertEquals(1000.0, balanceOf(target.get("accountNumber")));
    }

    @Test
    public void test_concurrent_deposits_are_not_lost() throws Exception {
        val account = createAccountWithInitialBalance(100);
        val accountNumber = account.get("accountNumber");

        val successes = runConcurrently(
                () -> accountService.cashDeposit(accountNumber, account.get("pin"), 100));

        Assertions.assertEquals(THREADS, successes);
        Assertions.assertEquals(100.0 * (THREADS + 1), balanceOf(accountNumber));
    }

    @Test
    public void test_concurrent_opposite_transfers_do_not_deadlock() throws Exception {
        val first = createAccountWithInitialBalance(100000);
        val second = createAccountWithInitialBalance(100000);
        val counter = new AtomicInteger();

        val successes = runConcurrently(() -> {
            if (counter.getAndIncrement() % 2 == 0) {
                accountService.fundTransfer(first.get("accountNumber"), second.get("accountNumber"),
                        first.get("pin"), 100);
            } else {
                accountService.fundTransfer(second.get("accountNumber"), first.get("accountNumber"),
                        second.get("pin"), 100);
            }
        });

        Assertions.assertEquals(THREADS, successes);
        Assertions.assertEquals(200000.0,
                balanceOf(first.get("accountNumber")) + balanceOf(second.get("accountNumber")));
    }
}
