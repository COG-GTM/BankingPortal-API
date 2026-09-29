package com.webapp.bankingportal.audit;

public enum AuditFailureReason {
    UNKNOWN_IDENTIFIER,
    BAD_CREDENTIALS,
    AUTHENTICATION_ERROR,
    INVALID_OTP,
    ACCOUNT_NOT_FOUND,
    VERIFICATION_FAILED,
    INVALID_NEW_PIN,
    OTHER
}
