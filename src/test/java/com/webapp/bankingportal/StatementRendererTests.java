package com.webapp.bankingportal;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.webapp.bankingportal.dto.AccountStatement;
import com.webapp.bankingportal.dto.StatementLine;
import com.webapp.bankingportal.entity.TransactionType;
import com.webapp.bankingportal.service.StatementCsvRenderer;
import com.webapp.bankingportal.service.StatementPdfRenderer;

import lombok.val;

public class StatementRendererTests {

    private static AccountStatement statement(String holderName, List<StatementLine> lines) {
        return new AccountStatement(
                "abc123",
                holderName,
                YearMonth.of(2026, 8),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                ZoneOffset.UTC,
                new BigDecimal("200.00"),
                new BigDecimal("900.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("300.00"),
                lines,
                LocalDateTime.of(2026, 9, 15, 10, 0));
    }

    private static List<StatementLine> sampleLines() {
        return List.of(
                new StatementLine(1L, LocalDateTime.of(2026, 8, 2, 9, 0), TransactionType.CASH_DEPOSIT,
                        "Cash deposit", null, new BigDecimal("1000.00"), new BigDecimal("1200.00")),
                new StatementLine(2L, LocalDateTime.of(2026, 8, 10, 9, 0), TransactionType.CASH_TRANSFER,
                        "Transfer to zzz999, \"savings\"", new BigDecimal("300.00"), null, new BigDecimal("900.00")));
    }

    @Test
    public void test_csv_renderer_output() {
        val csv = new String(new StatementCsvRenderer().render(statement("Jane Doe", sampleLines())),
                StandardCharsets.UTF_8);

        val expected = String.join("\r\n",
                "Date,Transaction ID,Type,Description,Debit,Credit,Balance",
                "2026-08-01 00:00:00,,OPENING_BALANCE,Opening balance,,,200.00",
                "2026-08-02 09:00:00,1,CASH_DEPOSIT,Cash deposit,,1000.00,1200.00",
                "2026-08-10 09:00:00,2,CASH_TRANSFER,\"Transfer to zzz999, \"\"savings\"\"\",300.00,,900.00",
                "2026-08-31 23:59:59,,CLOSING_BALANCE,Closing balance,300.00,1000.00,900.00",
                "");
        Assertions.assertEquals(expected, csv);
    }

    @Test
    public void test_pdf_renderer_output_contains_statement_details() throws Exception {
        val pdf = new StatementPdfRenderer().render(statement("Jane Doe", sampleLines()));

        try (val document = Loader.loadPDF(pdf)) {
            Assertions.assertEquals(1, document.getNumberOfPages());
            val text = new PDFTextStripper().getText(document);
            Assertions.assertTrue(text.contains("Account Statement"));
            Assertions.assertTrue(text.contains("abc123"));
            Assertions.assertTrue(text.contains("Jane Doe"));
            Assertions.assertTrue(text.contains("2026-08-01 to 2026-08-31"));
            Assertions.assertTrue(text.contains("Cash deposit"));
            Assertions.assertTrue(text.contains("1200.00"));
            Assertions.assertTrue(text.contains("900.00"));
        }
    }

    @Test
    public void test_pdf_renderer_paginates_long_statements() throws Exception {
        val lines = new ArrayList<StatementLine>();
        for (long i = 1; i <= 120; i++) {
            lines.add(new StatementLine(i, LocalDateTime.of(2026, 8, 2, 9, 0), TransactionType.CASH_DEPOSIT,
                    "Cash deposit", null, new BigDecimal("1.00"), new BigDecimal("1.00")));
        }

        val pdf = new StatementPdfRenderer().render(statement("Jane Doe", lines));

        try (val document = Loader.loadPDF(pdf)) {
            Assertions.assertTrue(document.getNumberOfPages() > 1);
            val text = new PDFTextStripper().getText(document);
            Assertions.assertTrue(text.contains("Page " + document.getNumberOfPages()));
        }
    }

    @Test
    public void test_pdf_renderer_handles_characters_outside_standard_fonts() throws Exception {
        val pdf = new StatementPdfRenderer().render(statement("李雷 Müller", List.of()));

        try (val document = Loader.loadPDF(pdf)) {
            val text = new PDFTextStripper().getText(document);
            Assertions.assertTrue(text.contains("?? Müller"));
            Assertions.assertTrue(text.contains("No transactions in this period."));
        }
    }

}
