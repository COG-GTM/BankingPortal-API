package com.webapp.bankingportal.exception;

public class DailyTransferLimitExceededException extends FundTransferException {

    public DailyTransferLimitExceededException(String message) {
        super(message);
    }

}
