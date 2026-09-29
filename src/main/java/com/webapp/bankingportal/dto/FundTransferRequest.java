package com.webapp.bankingportal.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record FundTransferRequest(
        String sourceAccountNumber,

        @NotBlank(message = "Target account number cannot be empty")
        String targetAccountNumber,

        @Positive(message = "Amount must be greater than 0")
        @DecimalMax(value = "100000", message = "Amount cannot be greater than 100,000")
        double amount,

        @NotBlank(message = "PIN cannot be empty")
        @Pattern(regexp = "\\d{4}", message = "PIN must be 4 digits")
        String pin) {
}
