package com.webapp.bankingportal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.webapp.bankingportal.entity.Transaction;

import jakarta.persistence.LockModeType;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    // Add any custom query methods here, if needed
	
    List<Transaction> findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(String sourceAccountNumber, String targetAccountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.id = :id")
    Optional<Transaction> findByIdForUpdate(@Param("id") Long id);

    boolean existsByReversedTransaction_Id(Long transactionId);
}
