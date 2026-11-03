package com.webapp.bankingportal.dto;

public record RegisterUserRequest(
        String name,
        String password,
        String email,
        String countryCode,
        String phoneNumber,
        String address) {
}
