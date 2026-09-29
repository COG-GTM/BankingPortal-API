package com.webapp.bankingportal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.webapp.bankingportal.dto.FundTransferRequest;
import com.webapp.bankingportal.dto.LoginRequest;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.exception.InsufficientBalanceException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.repository.TransactionRepository;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;

import lombok.val;

/**
 * Runs real concurrent requests against the database, so each service call
 * must commit in its own transaction instead of the rolled-back test
 * transaction inherited from {@link BaseTest}.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class FundTransferConcurrencyTests extends BaseTest {

    private static final long TIMEOUT_SECONDS = 120;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private record Outcomes(int successes, List<Throwable> failures) {
    }

    private static Outcomes runConcurrently(List<Callable<Void>> tasks) throws InterruptedException {
        val executor = Executors.newFixedThreadPool(tasks.size());
        val ready = new CountDownLatch(tasks.size());
        val start = new CountDownLatch(1);
        val futures = new ArrayList<Future<Void>>();

        try {
            for (val task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }

            Assertions.assertTrue(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            start.countDown();

            int successes = 0;
            val failures = new ArrayList<Throwable>();
            for (val future : futures) {
                try {
                    future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                } catch (TimeoutException e) {
                    Assertions.fail("Concurrent operation did not finish in time (possible deadlock)", e);
                }
            }
            return new Outcomes(successes, failures);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Rows committed here outlive the test, so users get guaranteed-unique email
     * and phone values instead of the region-dependent random ones from
     * {@link BaseTest#createUser()}, which can fall back to a fixed example number.
     */
    private Map<String, String> newAccount(double initialBalance) {
        val user = createUser();
        val password = user.getPassword();
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setCountryCode("US");
        user.setPhoneNumber(getRandomPhoneNumber("US"));
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);

        val accountNumber = accountService.createAccount(user).getAccountNumber();
        val pin = getRandomPin();
        accountService.createPin(accountNumber, password, pin);
        if (initialBalance > 0) {
            accountService.cashDeposit(accountNumber, pin, initialBalance);
        }

        val details = new HashMap<String, String>();
        details.put("accountNumber", accountNumber);
        details.put("password", password);
        details.put("pin", pin);
        return details;
    }

    private Map<String, String> newLoggedInAccount(double initialBalance) throws Exception {
        val details = newAccount(initialBalance);
        val loginRequest = new LoginRequest(details.get("accountNumber"), details.get("password"));
        val response = mockMvc.perform(MockMvcRequestBuilders
                .post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(loginRequest)))
                .andReturn()
                .getResponse();
        Assertions.assertEquals(200, response.getStatus(), response.getContentAsString());
        details.put("token", JsonPath.read(response.getContentAsString(), "$.token"));
        return details;
    }

    private double balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).getBalance();
    }

    private long transferCount(String sourceAccountNumber) {
        return transactionRepository
                .findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(sourceAccountNumber,
                        sourceAccountNumber)
                .stream()
                .filter(t -> t.getTransactionType() == TransactionType.CASH_TRANSFER)
                .filter(t -> t.getSourceAccount().getAccountNumber().equals(sourceAccountNumber))
                .count();
    }

    private Callable<Void> transfer(Map<String, String> source, String targetAccountNumber,
            double amount) {
        return () -> {
            accountService.fundTransfer(source.get("accountNumber"), targetAccountNumber, source.get("pin"),
                    amount);
            return null;
        };
    }

    @Test
    public void test_concurrent_transfers_from_same_account_keep_balances_consistent() throws Exception {
        val initialBalance = 10_000.0;
        val amount = 100.0;
        val transfers = 20;

        val source = newAccount(initialBalance);
        val target = newAccount(0);

        val tasks = new ArrayList<Callable<Void>>();
        for (int i = 0; i < transfers; i++) {
            tasks.add(transfer(source, target.get("accountNumber"), amount));
        }

        val outcomes = runConcurrently(tasks);

        Assertions.assertEquals(List.of(), outcomes.failures());
        Assertions.assertEquals(transfers, outcomes.successes());
        Assertions.assertEquals(initialBalance - transfers * amount, balanceOf(source.get("accountNumber")), 0.001);
        Assertions.assertEquals(transfers * amount, balanceOf(target.get("accountNumber")), 0.001);
        Assertions.assertEquals(transfers, transferCount(source.get("accountNumber")));
    }

    @Test
    public void test_concurrent_transfers_cannot_overdraw_source_account() throws Exception {
        val initialBalance = 1_000.0;
        val amount = 100.0;
        val transfers = 25;
        val expectedSuccesses = (int) (initialBalance / amount);

        val source = newAccount(initialBalance);
        val targets = new ArrayList<String>();
        for (int i = 0; i < 5; i++) {
            targets.add(newAccount(0).get("accountNumber"));
        }

        val tasks = new ArrayList<Callable<Void>>();
        for (int i = 0; i < transfers; i++) {
            tasks.add(transfer(source, targets.get(i % targets.size()), amount));
        }

        val outcomes = runConcurrently(tasks);

        Assertions.assertEquals(expectedSuccesses, outcomes.successes());
        Assertions.assertEquals(transfers - expectedSuccesses, outcomes.failures().size());
        outcomes.failures().forEach(
                failure -> Assertions.assertInstanceOf(InsufficientBalanceException.class, failure));

        Assertions.assertEquals(0.0, balanceOf(source.get("accountNumber")), 0.001);
        val totalReceived = targets.stream().mapToDouble(this::balanceOf).sum();
        Assertions.assertEquals(initialBalance, totalReceived, 0.001);
        Assertions.assertEquals(expectedSuccesses, transferCount(source.get("accountNumber")));
    }

    @Test
    public void test_concurrent_transfers_into_same_target_account() throws Exception {
        val initialBalance = 1_000.0;
        val amount = 500.0;
        val sourceCount = 12;

        val target = newAccount(0);
        val sources = new ArrayList<Map<String, String>>();
        for (int i = 0; i < sourceCount; i++) {
            sources.add(newAccount(initialBalance));
        }

        val tasks = new ArrayList<Callable<Void>>();
        for (val source : sources) {
            tasks.add(transfer(source, target.get("accountNumber"), amount));
        }

        val outcomes = runConcurrently(tasks);

        Assertions.assertEquals(List.of(), outcomes.failures());
        Assertions.assertEquals(sourceCount * amount, balanceOf(target.get("accountNumber")), 0.001);
        for (val source : sources) {
            Assertions.assertEquals(initialBalance - amount, balanceOf(source.get("accountNumber")), 0.001);
        }
    }

    @Test
    public void test_concurrent_bidirectional_transfers_do_not_deadlock() throws Exception {
        val initialBalance = 10_000.0;
        val amount = 100.0;
        val transfersPerDirection = 15;

        val first = newAccount(initialBalance);
        val second = newAccount(initialBalance);

        val tasks = new ArrayList<Callable<Void>>();
        for (int i = 0; i < transfersPerDirection; i++) {
            tasks.add(transfer(first, second.get("accountNumber"), amount));
            tasks.add(transfer(second, first.get("accountNumber"), amount));
        }

        val outcomes = runConcurrently(tasks);

        Assertions.assertEquals(List.of(), outcomes.failures());
        Assertions.assertEquals(initialBalance, balanceOf(first.get("accountNumber")), 0.001);
        Assertions.assertEquals(initialBalance, balanceOf(second.get("accountNumber")), 0.001);
        Assertions.assertEquals(transfersPerDirection, transferCount(first.get("accountNumber")));
        Assertions.assertEquals(transfersPerDirection, transferCount(second.get("accountNumber")));
    }

    @Test
    public void test_concurrent_transfers_deposits_and_withdrawals_on_same_account() throws Exception {
        val initialBalance = 5_000.0;
        val amount = 100.0;
        val operationsPerType = 10;

        val source = newAccount(initialBalance);
        val target = newAccount(0);
        val accountNumber = source.get("accountNumber");
        val pin = source.get("pin");

        val tasks = new ArrayList<Callable<Void>>();
        for (int i = 0; i < operationsPerType; i++) {
            tasks.add(transfer(source, target.get("accountNumber"), amount));
            tasks.add(() -> {
                accountService.cashDeposit(accountNumber, pin, amount);
                return null;
            });
            tasks.add(() -> {
                accountService.cashWithdrawal(accountNumber, pin, amount);
                return null;
            });
        }

        val outcomes = runConcurrently(tasks);

        Assertions.assertEquals(List.of(), outcomes.failures());
        Assertions.assertEquals(initialBalance - operationsPerType * amount, balanceOf(accountNumber), 0.001);
        Assertions.assertEquals(operationsPerType * amount, balanceOf(target.get("accountNumber")), 0.001);
    }

    private Callable<Void> transferViaApi(Map<String, String> source, String targetAccountNumber,
            double amount) {
        val request = new FundTransferRequest(source.get("accountNumber"), targetAccountNumber, amount,
                source.get("pin"));
        return () -> {
            val response = mockMvc.perform(MockMvcRequestBuilders
                    .post("/api/account/fund-transfer")
                    .header("Authorization", "Bearer " + source.get("token"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(JsonUtil.toJson(request)))
                    .andReturn()
                    .getResponse();
            val body = response.getContentAsString();
            Assertions.assertEquals(200, response.getStatus(), body);
            Assertions.assertEquals(ApiMessages.CASH_TRANSFER_SUCCESS.getMessage(), body);
            return null;
        };
    }

    @Test
    public void test_concurrent_transfers_via_api_keep_balances_consistent() throws Exception {
        val initialBalance = 50_000.0;
        val transfers = 20;

        val source = newLoggedInAccount(initialBalance);
        val target = newAccount(0);

        // Distinct amounts so no request is treated as an idempotent replay of another.
        val tasks = new ArrayList<Callable<Void>>();
        var totalTransferred = 0.0;
        for (int i = 1; i <= transfers; i++) {
            tasks.add(transferViaApi(source, target.get("accountNumber"), i * 100.0));
            totalTransferred += i * 100.0;
        }

        val outcomes = runConcurrently(tasks);

        Assertions.assertEquals(List.of(), outcomes.failures());
        Assertions.assertEquals(initialBalance - totalTransferred, balanceOf(source.get("accountNumber")), 0.001);
        Assertions.assertEquals(totalTransferred, balanceOf(target.get("accountNumber")), 0.001);
        Assertions.assertEquals(transfers, transferCount(source.get("accountNumber")));
    }

    @Test
    public void test_concurrent_duplicate_transfer_requests_are_applied_once() throws Exception {
        val initialBalance = 1_000.0;
        val amount = 100.0;
        val duplicates = 10;

        val source = newLoggedInAccount(initialBalance);
        val target = newAccount(0);

        val tasks = new ArrayList<Callable<Void>>();
        for (int i = 0; i < duplicates; i++) {
            tasks.add(transferViaApi(source, target.get("accountNumber"), amount));
        }

        val outcomes = runConcurrently(tasks);

        Assertions.assertEquals(List.of(), outcomes.failures());
        Assertions.assertEquals(initialBalance - amount, balanceOf(source.get("accountNumber")), 0.001);
        Assertions.assertEquals(amount, balanceOf(target.get("accountNumber")), 0.001);
        Assertions.assertEquals(1, transferCount(source.get("accountNumber")));
    }

    @Test
    public void test_stale_account_write_is_rejected() throws Exception {
        val account = newAccount(1_000.0);
        val accountNumber = account.get("accountNumber");

        val stale = accountRepository.findByAccountNumber(accountNumber);
        accountService.cashDeposit(accountNumber, account.get("pin"), 500.0);

        stale.setBalance(0.0);
        Assertions.assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> accountRepository.save(stale));
        Assertions.assertEquals(1_500.0, balanceOf(accountNumber), 0.001);
    }
}
