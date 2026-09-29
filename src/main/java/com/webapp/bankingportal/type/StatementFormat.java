package com.webapp.bankingportal.type;

import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;

import com.webapp.bankingportal.util.ApiMessages;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StatementFormat {
    CSV(new MediaType("text", "csv", StandardCharsets.UTF_8), "csv"),
    PDF(MediaType.APPLICATION_PDF, "pdf");

    private final MediaType mediaType;
    private final String fileExtension;

    public static StatementFormat fromValue(String value) {
        for (StatementFormat format : values()) {
            if (format.name().equalsIgnoreCase(value)) {
                return format;
            }
        }
        throw new IllegalArgumentException(
                String.format(ApiMessages.STATEMENT_FORMAT_INVALID_ERROR.getMessage(), value));
    }
}
