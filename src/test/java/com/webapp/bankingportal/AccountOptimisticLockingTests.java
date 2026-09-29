package com.webapp.bankingportal;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.webapp.bankingportal.dto.AmountRequest;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.exception.ConcurrentUpdateException;
import com.webapp.bankingportal.repository.AccountRepository;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;
import com.webapp.bankingportal.util.OptimisticLockRetryExecutor;

import lombok.val;

/**
 * Forces real optimistic locking conflicts: after the service reads the
 * account inside its transaction, a separate connection commits a version
 * bump on the same row, so the service's versioned UPDATE matches no rows.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "bankingportal.optimistic-lock.max-attempts=3",
        "bankingportal.optimistic-lock.backoff-ms=0"
})
public class AccountOptimisticLockingTests extends BaseTest {

    private static final Object CONFLICT_INJECTED = new Object();

    @SpyBean
    private AccountRepository accountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OptimisticLockRetryExecutor optimisticLockRetryExecutor;

    private final AtomicInteger conflictsToInject = new AtomicInteger();
    private final AtomicInteger conflictsInjected = new AtomicInteger();
    private volatile String conflictAccountNumber;

    @BeforeEach
    void injectConflictsOnRead() {
        val defaultAnswer = Mockito.mockingDetails(accountRepository).getMockCreationSettings().getDefaultAnswer();
        Mockito.doAnswer(invocation -> {
            val account = (Account) defaultAnswer.answer(invocation);
            if (account != null && account.getAccountNumber().equals(conflictAccountNumber)) {
                injectConflictOncePerTransaction(account.getAccountNumber());
            }
            return account;
        }).when(accountRepository).findByAccountNumber(Mockito.anyString());
    }

    @AfterEach
    void resetConflicts() {
        conflictAccountNumber = null;
        conflictsToInject.set(0);
        conflictsInjected.set(0);
    }

    private void injectConflictOncePerTransaction(String accountNumber) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.hasResource(CONFLICT_INJECTED)
                || conflictsToInject.getAndUpdate(n -> Math.max(0, n - 1)) == 0) {
            return;
        }

        TransactionSynchronizationManager.bindResource(CONFLICT_INJECTED, Boolean.TRUE);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                TransactionSynchronizationManager.unbindResourceIfPossible(CONFLICT_INJECTED);
            }
        });

        // Run on another thread so the update commits on its own connection.
        CompletableFuture.runAsync(() -> jdbcTemplate.update(
                "UPDATE account SET version = version + 1 WHERE account_number = ?", accountNumber)).join();
        conflictsInjected.incrementAndGet();
    }

    private double balanceOf(String accountNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_number = ?", Double.class, accountNumber);
    }

    private void deposit(Map<String, String> userDetails, double amount,
            ResultMatcher... expectations) throws Exception {
        val request = new AmountRequest(userDetails.get("accountNumber"), userDetails.get("pin"), amount);
        val result = mockMvc.perform(MockMvcRequestBuilders
                .post("/api/account/deposit")
                .header("Authorization", "Bearer " + userDetails.get("token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(request)));
        for (val expectation : expectations) {
            result.andExpect(expectation);
        }
    }

    @Test
    public void test_stale_account_update_is_rejected() {
        val accountNumber = createAccount().get("accountNumber");
        val stale = accountRepository.findByAccountNumber(accountNumber);
        val fresh = accountRepository.findByAccountNumber(accountNumber);

        fresh.setBalance(500);
        accountRepository.save(fresh);

        stale.setBalance(100);
        Assertions.assertThrows(OptimisticLockingFailureException.class, () -> accountRepository.save(stale));
        Assertions.assertEquals(500, balanceOf(accountNumber));
    }

    @Test
    public void test_deposit_succeeds_after_retrying_version_conflict() throws Exception {
        val userDetails = createAndLoginUserWithInitialBalance(1000);
        val accountNumber = userDetails.get("accountNumber");

        conflictAccountNumber = accountNumber;
        conflictsToInject.set(optimisticLockRetryExecutor.getMaxAttempts() - 1);

        deposit(userDetails, 500,
                MockMvcResultMatchers.status().isOk(),
                MockMvcResultMatchers.content().string(ApiMessages.CASH_DEPOSIT_SUCCESS.getMessage()));

        Assertions.assertEquals(optimisticLockRetryExecutor.getMaxAttempts() - 1, conflictsInjected.get());
        Assertions.assertEquals(1500, balanceOf(accountNumber));
    }

    @Test
    public void test_deposit_returns_409_when_retries_exhausted() throws Exception {
        val userDetails = createAndLoginUserWithInitialBalance(1000);
        val accountNumber = userDetails.get("accountNumber");

        conflictAccountNumber = accountNumber;
        conflictsToInject.set(Integer.MAX_VALUE);

        deposit(userDetails, 500,
                MockMvcResultMatchers.status().isConflict(),
                MockMvcResultMatchers.jsonPath("$.message")
                        .value(ApiMessages.ACCOUNT_CONCURRENT_UPDATE_ERROR.getMessage()));

        Assertions.assertEquals(optimisticLockRetryExecutor.getMaxAttempts(), conflictsInjected.get());
        Assertions.assertEquals(1000, balanceOf(accountNumber));
    }

    @Test
    public void test_service_throws_concurrent_update_exception_when_retries_exhausted() {
        val accountDetails = createAccountWithPin(passwordEncoder, userRepository, accountService);
        val accountNumber = accountDetails.get("accountNumber");

        conflictAccountNumber = accountNumber;
        conflictsToInject.set(Integer.MAX_VALUE);

        val ex = Assertions.assertThrows(ConcurrentUpdateException.class,
                () -> accountService.cashDeposit(accountNumber, accountDetails.get("pin"), 500));

        Assertions.assertInstanceOf(OptimisticLockingFailureException.class, ex.getCause());
        Assertions.assertEquals(optimisticLockRetryExecutor.getMaxAttempts(), conflictsInjected.get());
        Assertions.assertEquals(0, balanceOf(accountNumber));
    }

}
