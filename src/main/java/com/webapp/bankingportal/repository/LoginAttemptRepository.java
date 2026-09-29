package com.webapp.bankingportal.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import com.webapp.bankingportal.entity.LoginAttempt;

import jakarta.persistence.LockModeType;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    Optional<LoginAttempt> findByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LoginAttempt> findForUpdateByAccountNumber(String accountNumber);

    void deleteByAccountNumber(String accountNumber);
}
