package com.webapp.bankingportal.dto;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
