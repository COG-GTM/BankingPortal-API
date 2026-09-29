package com.webapp.bankingportal.dto;

public record CredentialStatusResponse(
        String passwordChangedAt,
        boolean passwordRotationDue,
        boolean pinCreated,
        String pinChangedAt,
        boolean pinRotationDue) {
}
