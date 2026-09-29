package com.webapp.bankingportal.service;

import java.util.List;

import com.webapp.bankingportal.dto.PayeeResponse;

public interface PayeeService {

    PayeeResponse addPayee(String ownerAccountNumber, String payeeAccountNumber, String nickname);

    List<PayeeResponse> getPayees(String ownerAccountNumber);

    PayeeResponse verifyPayee(String ownerAccountNumber, Long payeeId, String pin);

    void removePayee(String ownerAccountNumber, Long payeeId);

    /**
     * Resolves the account number a fund transfer should be sent to. When a
     * payee ID is given it must belong to the owner and be verified; if a raw
     * target account number is also given it must match the payee's account.
     */
    String resolveTransferTarget(String ownerAccountNumber, Long payeeId, String targetAccountNumber);
}
