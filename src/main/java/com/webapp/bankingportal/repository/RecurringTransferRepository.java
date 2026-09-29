package com.webapp.bankingportal.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.webapp.bankingportal.entity.RecurringTransfer;
import com.webapp.bankingportal.entity.RecurringTransferStatus;

@Repository
public interface RecurringTransferRepository extends JpaRepository<RecurringTransfer, Long> {

    List<RecurringTransfer> findByStatusAndNextExecutionDateLessThanEqual(RecurringTransferStatus status,
            LocalDate date);

    List<RecurringTransfer> findBySourceAccount_AccountNumberOrderByIdAsc(String accountNumber);
}
