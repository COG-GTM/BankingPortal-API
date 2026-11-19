package com.webapp.bankingportal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.webapp.bankingportal.dto.TransactionDTO;
import com.webapp.bankingportal.entity.Transaction;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    // Add any custom query methods here, if needed
	
    List<Transaction> findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber(String sourceAccountNumber, String targetAccountNumber);

    @Query("SELECT new com.webapp.bankingportal.dto.TransactionDTO(t.id, t.amount, t.transactionType, t.transactionDate, "
            + "sa.accountNumber, COALESCE(ta.accountNumber, 'N/A')) "
            + "FROM Transaction t LEFT JOIN t.sourceAccount sa LEFT JOIN t.targetAccount ta "
            + "WHERE sa.accountNumber = :accountNumber OR ta.accountNumber = :accountNumber "
            + "ORDER BY t.id")
    List<TransactionDTO> findTransactionDTOsByAccountNumber(@Param("accountNumber") String accountNumber);
}
