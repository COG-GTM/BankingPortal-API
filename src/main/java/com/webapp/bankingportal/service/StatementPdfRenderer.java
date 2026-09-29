package com.webapp.bankingportal.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import com.webapp.bankingportal.dto.AccountStatement;
import com.webapp.bankingportal.dto.StatementLine;

import lombok.val;

/**
 * Renders a statement as a paginated A4 PDF using PDFBox's built-in
 * Helvetica fonts.
 */
@Component
public class StatementPdfRenderer {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_TIME_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final float MARGIN = 50f;
    private static final float ROW_HEIGHT = 15f;
    private static final float BODY_FONT_SIZE = 9f;
    private static final float HEADER_FONT_SIZE = 10f;
    private static final float TITLE_FONT_SIZE = 16f;

    private static final float DATE_X = MARGIN;
    private static final float DESCRIPTION_X = 140f;
    private static final float DESCRIPTION_MAX_WIDTH = 190f;
    private static final float DEBIT_RIGHT_X = 400f;
    private static final float CREDIT_RIGHT_X = 472f;
    private static final float BALANCE_RIGHT_X = 545f;

    public byte[] render(AccountStatement statement) {
        try (val document = new PDDocument(); val out = new ByteArrayOutputStream()) {
            new Writer(document).write(statement);
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render PDF statement", e);
        }
    }

    private static final class Writer {

        private final PDDocument document;
        private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream content;
        private float y;
        private int pageNumber;

        Writer(PDDocument document) {
            this.document = document;
        }

        void write(AccountStatement statement) throws IOException {
            newPage();
            try {
                text(bold, TITLE_FONT_SIZE, MARGIN, y, "Account Statement");
                y -= ROW_HEIGHT * 2;

                summaryRow("Account number", statement.accountNumber());
                summaryRow("Account holder", statement.accountHolderName());
                summaryRow("Statement period", statement.periodStart() + " to " + statement.periodEnd()
                        + " (" + statement.zone().getId() + ")");
                summaryRow("Opening balance", amount(statement.openingBalance()));
                summaryRow("Total credits", amount(statement.totalCredits()));
                summaryRow("Total debits", amount(statement.totalDebits()));
                summaryRow("Closing balance", amount(statement.closingBalance()));
                summaryRow("Generated at", DATE_TIME_SECONDS.format(statement.generatedAt()));
                y -= ROW_HEIGHT;

                tableHeader();
                if (statement.lines().isEmpty()) {
                    text(regular, BODY_FONT_SIZE, DATE_X, y, "No transactions in this period.");
                }
                for (StatementLine line : statement.lines()) {
                    if (y < MARGIN + ROW_HEIGHT) {
                        newPage();
                        tableHeader();
                    }
                    tableRow(line);
                }
            } finally {
                content.close();
            }
        }

        private void newPage() throws IOException {
            if (content != null) {
                content.close();
            }
            val page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            pageNumber++;
            val pageLabel = "Page " + pageNumber;
            rightAlignedText(regular, BODY_FONT_SIZE, BALANCE_RIGHT_X, MARGIN / 2, pageLabel);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private void summaryRow(String label, String value) throws IOException {
            text(bold, HEADER_FONT_SIZE, MARGIN, y, label + ":");
            text(regular, HEADER_FONT_SIZE, MARGIN + 110, y, value);
            y -= ROW_HEIGHT;
        }

        private void tableHeader() throws IOException {
            text(bold, HEADER_FONT_SIZE, DATE_X, y, "Date");
            text(bold, HEADER_FONT_SIZE, DESCRIPTION_X, y, "Description");
            rightAlignedText(bold, HEADER_FONT_SIZE, DEBIT_RIGHT_X, y, "Debit");
            rightAlignedText(bold, HEADER_FONT_SIZE, CREDIT_RIGHT_X, y, "Credit");
            rightAlignedText(bold, HEADER_FONT_SIZE, BALANCE_RIGHT_X, y, "Balance");
            content.moveTo(MARGIN, y - 4);
            content.lineTo(BALANCE_RIGHT_X, y - 4);
            content.stroke();
            y -= ROW_HEIGHT;
        }

        private void tableRow(StatementLine line) throws IOException {
            text(regular, BODY_FONT_SIZE, DATE_X, y, DATE_TIME.format(line.date()));
            text(regular, BODY_FONT_SIZE, DESCRIPTION_X, y,
                    truncate(line.description(), DESCRIPTION_MAX_WIDTH, BODY_FONT_SIZE));
            rightAlignedText(regular, BODY_FONT_SIZE, DEBIT_RIGHT_X, y, amount(line.debit()));
            rightAlignedText(regular, BODY_FONT_SIZE, CREDIT_RIGHT_X, y, amount(line.credit()));
            rightAlignedText(regular, BODY_FONT_SIZE, BALANCE_RIGHT_X, y, amount(line.balance()));
            y -= ROW_HEIGHT;
        }

        private void text(PDFont font, float size, float x, float baseline, String value) throws IOException {
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, baseline);
            content.showText(encodable(font, value));
            content.endText();
        }

        private void rightAlignedText(PDFont font, float size, float rightX, float baseline, String value)
                throws IOException {
            val safe = encodable(font, value);
            text(font, size, rightX - width(font, size, safe), baseline, safe);
        }

        private String truncate(String value, float maxWidth, float size) throws IOException {
            var safe = encodable(regular, value);
            if (width(regular, size, safe) <= maxWidth) {
                return safe;
            }
            while (!safe.isEmpty() && width(regular, size, safe + "...") > maxWidth) {
                safe = safe.substring(0, safe.length() - 1);
            }
            return safe + "...";
        }

        private static float width(PDFont font, float size, String value) throws IOException {
            return font.getStringWidth(value) / 1000 * size;
        }

        /**
         * Standard 14 fonts only cover WinAnsiEncoding; replace anything else
         * (e.g. non-Latin names) rather than failing the whole statement.
         */
        private static String encodable(PDFont font, String value) {
            if (value == null) {
                return "";
            }
            val result = new StringBuilder(value.length());
            value.codePoints().forEach(codePoint -> {
                val character = new String(Character.toChars(codePoint));
                try {
                    font.encode(character);
                    result.append(character);
                } catch (IllegalArgumentException | IOException e) {
                    result.append('?');
                }
            });
            return result.toString();
        }

        private static String amount(BigDecimal value) {
            return value == null ? "" : value.toPlainString();
        }
    }

}
