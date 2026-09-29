package com.webapp.bankingportal.service;

import java.util.List;

import com.webapp.bankingportal.dto.VelocityViolation;
import com.webapp.bankingportal.entity.Transaction;
import com.webapp.bankingportal.entity.TransactionReviewFlag;

public interface TransactionVelocityService {

    /**
     * Evaluates a pending transfer against the velocity rules. Must be called
     * before the transfer itself is persisted, since the current transfer is
     * added on top of the already-recorded daily totals.
     */
    List<VelocityViolation> evaluateTransfer(String sourceAccountNumber, double amount);

    List<TransactionReviewFlag> flagForReview(Transaction transaction, List<VelocityViolation> violations);

}
