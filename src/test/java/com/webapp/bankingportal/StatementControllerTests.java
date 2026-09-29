package com.webapp.bankingportal;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.webapp.bankingportal.util.ApiMessages;

import lombok.val;

public class StatementControllerTests extends BaseTest {

    private static final String STATEMENT_URL = "/api/account/statement";

    private String currentMonth() {
        return YearMonth.now(ZoneOffset.UTC).toString();
    }

    private HashMap<String, String> accountWithActivity() throws Exception {
        val accountDetails = createAndLoginUser();
        val accountNumber = accountDetails.get("accountNumber");
        val pin = getRandomPin();
        accountService.createPin(accountNumber, accountDetails.get("password"), pin);
        accountService.cashDeposit(accountNumber, pin, 1000);
        accountService.cashWithdrawal(accountNumber, pin, 300);
        return accountDetails;
    }

    @Test
    public void test_statement_csv_for_current_month() throws Exception {
        val accountDetails = accountWithActivity();
        val accountNumber = accountDetails.get("accountNumber");

        val result = mockMvc.perform(MockMvcRequestBuilders
                .get(STATEMENT_URL)
                .param("month", currentMonth())
                .param("format", "csv")
                .header("Authorization", "Bearer " + accountDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith("text/csv"))
                .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.CONTENT_DISPOSITION,
                        CoreMatchers.containsString(
                                "attachment; filename=\"statement-" + accountNumber + "-" + currentMonth() + ".csv\"")))
                .andReturn();

        val csv = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        val rows = csv.split("\r\n");
        Assertions.assertEquals(5, rows.length);
        Assertions.assertEquals("Date,Transaction ID,Type,Description,Debit,Credit,Balance", rows[0]);
        Assertions.assertTrue(rows[1].endsWith(",OPENING_BALANCE,Opening balance,,,0.00"));
        Assertions.assertTrue(rows[2].contains(",CASH_DEPOSIT,Cash deposit,,1000.00,1000.00"));
        Assertions.assertTrue(rows[3].contains(",CASH_WITHDRAWAL,Cash withdrawal,300.00,,700.00"));
        Assertions.assertTrue(rows[4].endsWith(",CLOSING_BALANCE,Closing balance,300.00,1000.00,700.00"));
    }

    @Test
    public void test_statement_defaults_to_csv() throws Exception {
        val accountDetails = accountWithActivity();

        mockMvc.perform(MockMvcRequestBuilders
                .get(STATEMENT_URL)
                .param("month", currentMonth())
                .header("Authorization", "Bearer " + accountDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith("text/csv"));
    }

    @Test
    public void test_statement_pdf_for_current_month() throws Exception {
        val accountDetails = accountWithActivity();
        val accountNumber = accountDetails.get("accountNumber");

        val result = mockMvc.perform(MockMvcRequestBuilders
                .get(STATEMENT_URL)
                .param("month", currentMonth())
                .param("format", "PDF")
                .header("Authorization", "Bearer " + accountDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.CONTENT_DISPOSITION,
                        CoreMatchers.containsString(".pdf\"")))
                .andReturn();

        try (val document = Loader.loadPDF(result.getResponse().getContentAsByteArray())) {
            val text = new PDFTextStripper().getText(document);
            Assertions.assertTrue(text.contains(accountNumber));
            Assertions.assertTrue(text.contains("Cash withdrawal"));
            Assertions.assertTrue(text.contains("700.00"));
        }
    }

    @Test
    public void test_statement_with_invalid_month() throws Exception {
        val accountDetails = accountWithActivity();

        mockMvc.perform(MockMvcRequestBuilders
                .get(STATEMENT_URL)
                .param("month", "2026-13")
                .header("Authorization", "Bearer " + accountDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().string(
                        String.format(ApiMessages.STATEMENT_MONTH_INVALID_ERROR.getMessage(), "2026-13")));
    }

    @Test
    public void test_statement_with_future_month() throws Exception {
        val accountDetails = accountWithActivity();

        mockMvc.perform(MockMvcRequestBuilders
                .get(STATEMENT_URL)
                .param("month", YearMonth.now(ZoneOffset.UTC).plusMonths(1).toString())
                .header("Authorization", "Bearer " + accountDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().string(
                        ApiMessages.STATEMENT_MONTH_IN_FUTURE_ERROR.getMessage()));
    }

    @Test
    public void test_statement_with_invalid_format() throws Exception {
        val accountDetails = accountWithActivity();

        mockMvc.perform(MockMvcRequestBuilders
                .get(STATEMENT_URL)
                .param("month", currentMonth())
                .param("format", "xlsx")
                .header("Authorization", "Bearer " + accountDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().string(
                        String.format(ApiMessages.STATEMENT_FORMAT_INVALID_ERROR.getMessage(), "xlsx")));
    }

    @Test
    public void test_statement_without_authorization() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                .get(STATEMENT_URL)
                .param("month", currentMonth()))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

}
