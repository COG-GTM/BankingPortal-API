package com.webapp.bankingportal.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.webapp.bankingportal.dto.DueRecurringTransfer;
import com.webapp.bankingportal.entity.RecurringTransfer;
import com.webapp.bankingportal.entity.RecurringTransferStatus;

@Repository
public interface RecurringTransferRepository extends JpaRepository<RecurringTransfer, Long> {

    List<RecurringTransfer> findBySourceAccount_AccountNumberOrderByIdDesc(String accountNumber);

    Optional<RecurringTransfer> findByIdAndSourceAccount_AccountNumber(Long id, String accountNumber);

    @Query("SELECT new com.webapp.bankingportal.dto.DueRecurringTransfer(r.id, r.nextExecutionDate) "
            + "FROM RecurringTransfer r WHERE r.status = :status AND r.nextExecutionDate <= :date "
            + "ORDER BY r.nextExecutionDate, r.id")
    List<DueRecurringTransfer> findDueOnOrBefore(@Param("status") RecurringTransferStatus status, @Param("date") LocalDate date);
}
