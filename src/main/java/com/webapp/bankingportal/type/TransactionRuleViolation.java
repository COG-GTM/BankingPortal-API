package com.webapp.bankingportal.type;

import java.util.function.Function;

import com.webapp.bankingportal.exception.FundTransferException;
import com.webapp.bankingportal.exception.InsufficientBalanceException;
import com.webapp.bankingportal.exception.InvalidAmountException;
import com.webapp.bankingportal.exception.NotFoundException;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum TransactionRuleViolation {
    AMOUNT_NOT_POSITIVE(ApiMessages.AMOUNT_NEGATIVE_ERROR, InvalidAmountException::new),
    AMOUNT_NOT_MULTIPLE_OF_100(ApiMessages.AMOUNT_NOT_MULTIPLE_OF_100_ERROR, InvalidAmountException::new),
    AMOUNT_EXCEEDS_TRANSACTION_LIMIT(ApiMessages.AMOUNT_EXCEED_100_000_ERROR, InvalidAmountException::new),
    SAME_ACCOUNT(ApiMessages.CASH_TRANSFER_SAME_ACCOUNT_ERROR, FundTransferException::new),
    TARGET_ACCOUNT_NOT_FOUND(ApiMessages.ACCOUNT_NOT_FOUND, NotFoundException::new),
    INSUFFICIENT_BALANCE(ApiMessages.BALANCE_INSUFFICIENT_ERROR, InsufficientBalanceException::new),
    ;

    private final ApiMessages apiMessage;
    private final Function<String, RuntimeException> exceptionFactory;

    public String getMessage() {
        return apiMessage.getMessage();
    }

    public RuntimeException toException() {
        return exceptionFactory.apply(getMessage());
    }
}
