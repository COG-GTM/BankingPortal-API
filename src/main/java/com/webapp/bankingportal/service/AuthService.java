package com.webapp.bankingportal.service;

import org.springframework.http.ResponseEntity;

import com.webapp.bankingportal.dto.OtpRequest;
import com.webapp.bankingportal.dto.OtpVerificationRequest;
import com.webapp.bankingportal.dto.RefreshTokenRequest;
import com.webapp.bankingportal.dto.ResetPasswordRequest;
import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.exception.InvalidTokenException;

public interface AuthService {
    public String generatePasswordResetToken(User user);

    public boolean verifyPasswordResetToken(String token, User user);

    public void deletePasswordResetToken(String token);

    public ResponseEntity<String> sendOtpForPasswordReset(OtpRequest otpRequest);

    public ResponseEntity<String> verifyOtpAndIssueResetToken(OtpVerificationRequest otpVerificationRequest);

    public ResponseEntity<String> resetPassword(ResetPasswordRequest resetPasswordRequest);

    public ResponseEntity<String> refreshToken(RefreshTokenRequest refreshTokenRequest) throws InvalidTokenException;

    public ResponseEntity<String> revokeRefreshToken(RefreshTokenRequest refreshTokenRequest);

}
