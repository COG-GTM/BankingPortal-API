package com.webapp.bankingportal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Fund transfer from the authenticated user's account to another account")
public record FundTransferRequest(
        @Schema(description = "Ignored; the source account is taken from the JWT", example = "a1c3d9",
                accessMode = Schema.AccessMode.READ_ONLY) String sourceAccountNumber,
        @Schema(description = "Account number to credit", example = "b7e2f1",
                requiredMode = Schema.RequiredMode.REQUIRED) String targetAccountNumber,
        @Schema(description = "Amount to transfer; > 0, multiple of 100, at most 100,000", example = "1000",
                minimum = "100", maximum = "100000", multipleOf = 100,
                requiredMode = Schema.RequiredMode.REQUIRED) double amount,
        @Schema(description = "4-digit account PIN", example = "1234", pattern = "^[0-9]{4}$",
                requiredMode = Schema.RequiredMode.REQUIRED) String pin) {
}
