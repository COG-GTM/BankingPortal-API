package com.webapp.bankingportal.service;

import java.time.YearMonth;

import com.webapp.bankingportal.dto.AccountStatement;
import com.webapp.bankingportal.type.StatementFormat;

public interface StatementService {

    /**
     * Parses a {@code yyyy-MM} month. A null or blank value resolves to the
     * previous calendar month.
     */
    YearMonth parseMonth(String month);

    AccountStatement getMonthlyStatement(String accountNumber, YearMonth month);

    byte[] generateMonthlyStatement(String accountNumber, YearMonth month, StatementFormat format);

}
