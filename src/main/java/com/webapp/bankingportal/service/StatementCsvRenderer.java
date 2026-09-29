package com.webapp.bankingportal.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Component;

import com.webapp.bankingportal.dto.AccountStatement;
import com.webapp.bankingportal.dto.StatementLine;

import lombok.val;

/**
 * Renders a statement as RFC 4180 CSV. Opening and closing balances are
 * emitted as rows so the file keeps a single, machine-readable schema.
 */
@Component
public class StatementCsvRenderer {

    static final List<String> HEADER = List.of(
            "Date", "Transaction ID", "Type", "Description", "Debit", "Credit", "Balance");

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String LINE_SEPARATOR = "\r\n";

    public byte[] render(AccountStatement statement) {
        val csv = new StringBuilder();
        appendRow(csv, HEADER);
        appendRow(csv, List.of(
                DATE_TIME.format(statement.periodStart().atStartOfDay()),
                "", "OPENING_BALANCE", "Opening balance", "", "",
                amount(statement.openingBalance())));

        for (StatementLine line : statement.lines()) {
            appendRow(csv, List.of(
                    DATE_TIME.format(line.date()),
                    String.valueOf(line.transactionId()),
                    line.type().name(),
                    line.description(),
                    amount(line.debit()),
                    amount(line.credit()),
                    amount(line.balance())));
        }

        appendRow(csv, List.of(
                DATE_TIME.format(statement.periodEnd().atTime(23, 59, 59)),
                "", "CLOSING_BALANCE", "Closing balance",
                amount(statement.totalDebits()),
                amount(statement.totalCredits()),
                amount(statement.closingBalance())));

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder csv, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escape(cells.get(i)));
        }
        csv.append(LINE_SEPARATOR);
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private static String amount(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

}
