package com.webapp.bankingportal.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.webapp.bankingportal.entity.Transaction;
import com.webapp.bankingportal.entity.TransactionType;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    // Add any custom query methods here, if needed
	
    List<Transaction> findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(String sourceAccountNumber, String targetAccountNumber);

    @Query("SELECT COALESCE(SUM(t.amount), 0.0) FROM Transaction t "
            + "WHERE t.sourceAccount.accountNumber = :accountNumber "
            + "AND t.transactionType = :transactionType "
            + "AND t.transactionDate >= :from AND t.transactionDate < :to")
    double sumAmountBySourceAccountAndTypeBetween(
            @Param("accountNumber") String accountNumber,
            @Param("transactionType") TransactionType transactionType,
            @Param("from") Date from,
            @Param("to") Date to);
}
