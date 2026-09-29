package com.webapp.bankingportal.type;

import com.webapp.bankingportal.util.ApiMessages;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum TransferWarning {
    APPROACHING_TRANSACTION_LIMIT(ApiMessages.TRANSFER_APPROACHING_LIMIT_WARNING),
    LOW_RESULTING_BALANCE(ApiMessages.TRANSFER_LOW_BALANCE_WARNING),
    ;

    private final ApiMessages apiMessage;

    public String getMessage() {
        return apiMessage.getMessage();
    }
}
