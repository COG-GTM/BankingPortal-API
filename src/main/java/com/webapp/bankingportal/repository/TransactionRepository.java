package com.webapp.bankingportal.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.webapp.bankingportal.dto.TransactionDTO;
import com.webapp.bankingportal.entity.Transaction;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    String HISTORY_SELECT = "select new com.webapp.bankingportal.dto.TransactionDTO("
            + "t.id, t.amount, t.transactionType, t.transactionDate, "
            + "s.accountNumber, coalesce(ta.accountNumber, 'N/A')) "
            + "from Transaction t join t.sourceAccount s left join t.targetAccount ta ";

    String HISTORY_FILTER = "where t.sourceAccount.id = :accountId or t.targetAccount.id = :accountId";

    @Query(value = HISTORY_SELECT + HISTORY_FILTER,
            countQuery = "select count(t) from Transaction t " + HISTORY_FILTER)
    Page<TransactionDTO> findHistoryByAccountId(@Param("accountId") Long accountId, Pageable pageable);

    @Query(HISTORY_SELECT + HISTORY_FILTER)
    List<TransactionDTO> findHistoryByAccountId(@Param("accountId") Long accountId, Sort sort);
}
