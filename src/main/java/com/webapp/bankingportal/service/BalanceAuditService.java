package com.webapp.bankingportal.service;

import java.time.Instant;

import com.webapp.bankingportal.dto.BalanceAuditTrailResponse;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.BalanceAuditLog;
import com.webapp.bankingportal.entity.Transaction;
import com.webapp.bankingportal.entity.TransactionType;

public interface BalanceAuditService {

    BalanceAuditLog record(Account account, TransactionType operation, double balanceBefore, Transaction transaction,
            String performedBy, String counterpartyAccountNumber);

    BalanceAuditTrailResponse getAuditTrail(String accountNumber, Instant from, Instant to, int page, int size);

}
