package com.webapp.bankingportal.repository;

import java.util.Date;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.webapp.bankingportal.entity.Account;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Account findByAccountNumber(String accountNumber);

    @Query("SELECT a.pinLockedUntil FROM Account a WHERE a.accountNumber = :accountNumber")
    Date findPinLockedUntil(@Param("accountNumber") String accountNumber);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Account a SET a.failedPinAttempts = 0, a.pinLockedUntil = null "
            + "WHERE a.accountNumber = :accountNumber AND a.pinLockedUntil <= :now")
    int clearExpiredPinLock(@Param("accountNumber") String accountNumber, @Param("now") Date now);

    /**
     * Atomically consumes one PIN attempt before the PIN is compared, so concurrent
     * guesses cannot exceed {@code maxAttempts}. Returns 0 when the PIN is locked.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Account a SET a.failedPinAttempts = a.failedPinAttempts + 1 "
            + "WHERE a.accountNumber = :accountNumber AND a.pinLockedUntil IS NULL "
            + "AND a.failedPinAttempts < :maxAttempts")
    int reservePinAttempt(@Param("accountNumber") String accountNumber, @Param("maxAttempts") int maxAttempts);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Account a SET a.failedPinAttempts = 0, a.pinLockedUntil = :lockedUntil "
            + "WHERE a.accountNumber = :accountNumber AND a.pinLockedUntil IS NULL "
            + "AND a.failedPinAttempts >= :maxAttempts")
    int lockPinIfAttemptsExhausted(@Param("accountNumber") String accountNumber,
            @Param("maxAttempts") int maxAttempts, @Param("lockedUntil") Date lockedUntil);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Account a SET a.failedPinAttempts = 0, a.pinLockedUntil = null "
            + "WHERE a.accountNumber = :accountNumber")
    int resetPinAttempts(@Param("accountNumber") String accountNumber);
}
