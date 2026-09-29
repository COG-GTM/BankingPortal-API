package com.webapp.bankingportal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.webapp.bankingportal.entity.ReviewStatus;
import com.webapp.bankingportal.entity.TransactionReviewFlag;

@Repository
public interface TransactionReviewFlagRepository extends JpaRepository<TransactionReviewFlag, Long> {

    List<TransactionReviewFlag> findByStatus(ReviewStatus status);

    List<TransactionReviewFlag> findByTransaction_Id(Long transactionId);

}
