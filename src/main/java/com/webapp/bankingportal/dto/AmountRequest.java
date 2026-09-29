package com.webapp.bankingportal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cash deposit or withdrawal on the authenticated user's account")
public record AmountRequest(
        @Schema(description = "Ignored; the account is taken from the JWT", example = "a1c3d9",
                accessMode = Schema.AccessMode.READ_ONLY) String accountNumber,
        @Schema(description = "4-digit account PIN", example = "1234", pattern = "^[0-9]{4}$",
                requiredMode = Schema.RequiredMode.REQUIRED) String pin,
        @Schema(description = "Amount; > 0, multiple of 100, at most 100,000", example = "500",
                minimum = "100", maximum = "100000", multipleOf = 100,
                requiredMode = Schema.RequiredMode.REQUIRED) double amount) {
}
