package com.webapp.bankingportal.dto;

public record UpdateUserRequest(
        String name,
        String email,
        String countryCode,
        String phoneNumber,
        String address,
        String password) {
}
