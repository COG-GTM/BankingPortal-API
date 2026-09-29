package com.webapp.bankingportal.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.webapp.bankingportal.entity.ScheduledTransfer;
import com.webapp.bankingportal.entity.ScheduledTransferStatus;

@Repository
public interface ScheduledTransferRepository extends JpaRepository<ScheduledTransfer, Long> {

    @Query("SELECT st.id FROM ScheduledTransfer st WHERE st.status = :status AND st.scheduledAt <= :now ORDER BY st.scheduledAt ASC")
    List<Long> findIdsByStatusDueBefore(@Param("status") ScheduledTransferStatus status,
            @Param("now") LocalDateTime now);

    List<ScheduledTransfer> findBySourceAccount_AccountNumberOrderByScheduledAtDesc(String accountNumber);

    Optional<ScheduledTransfer> findByIdAndSourceAccount_AccountNumber(Long id, String accountNumber);
}
