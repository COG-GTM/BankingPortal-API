package com.webapp.bankingportal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

public record AccountStatement(
        String accountNumber,
        String accountHolderName,
        YearMonth month,
        LocalDate periodStart,
        LocalDate periodEnd,
        ZoneId zone,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal totalCredits,
        BigDecimal totalDebits,
        List<StatementLine> lines,
        LocalDateTime generatedAt) {
}
