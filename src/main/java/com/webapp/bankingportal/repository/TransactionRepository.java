package com.webapp.bankingportal.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.webapp.bankingportal.entity.Transaction;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    // Add any custom query methods here, if needed
	
    List<Transaction> findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(String sourceAccountNumber, String targetAccountNumber);

    @Query("""
            SELECT t FROM Transaction t
            LEFT JOIN t.sourceAccount source
            LEFT JOIN t.targetAccount target
            WHERE (source.accountNumber = :accountNumber OR target.accountNumber = :accountNumber)
              AND t.transactionDate >= :from
            ORDER BY t.transactionDate ASC, t.id ASC
            """)
    List<Transaction> findByAccountNumberOnOrAfter(
            @Param("accountNumber") String accountNumber, @Param("from") Date from);
}
