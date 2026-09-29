package com.webapp.bankingportal.dto;

import java.time.Instant;
import java.util.List;

public record ValidationErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldValidationError> errors) {

    public record FieldValidationError(String field, String message) {
    }
}
