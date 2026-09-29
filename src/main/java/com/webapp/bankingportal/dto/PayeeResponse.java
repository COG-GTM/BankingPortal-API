package com.webapp.bankingportal.dto;

import java.util.Date;

import com.webapp.bankingportal.entity.Payee;

public record PayeeResponse(
        Long id,
        String nickname,
        String accountNumber,
        String accountHolderName,
        boolean verified,
        Date createdAt,
        Date verifiedAt) {

    public static PayeeResponse from(Payee payee) {
        return new PayeeResponse(
                payee.getId(),
                payee.getNickname(),
                payee.getPayeeAccount().getAccountNumber(),
                payee.getPayeeAccount().getUser().getName(),
                payee.isVerified(),
                payee.getCreatedAt(),
                payee.getVerifiedAt());
    }
}
