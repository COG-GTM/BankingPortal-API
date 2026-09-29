package com.webapp.bankingportal.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.webapp.bankingportal.dto.OtpRequest;
import com.webapp.bankingportal.dto.OtpVerificationRequest;
import com.webapp.bankingportal.dto.RefreshTokenRequest;
import com.webapp.bankingportal.dto.ResetPasswordRequest;
import com.webapp.bankingportal.exception.InvalidTokenException;
import com.webapp.bankingportal.service.AuthService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/password-reset/send-otp")
    public ResponseEntity<String> sendOtpForPasswordReset(@RequestBody OtpRequest otpRequest) {
        return authService.sendOtpForPasswordReset(otpRequest);
    }

    @PostMapping("/password-reset/verify-otp")
    public ResponseEntity<String> verifyOtpAndIssueResetToken(
            @RequestBody OtpVerificationRequest otpVerificationRequest) {
        return authService.verifyOtpAndIssueResetToken(otpVerificationRequest);
    }

    @PostMapping("/password-reset")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest resetPasswordRequest) {
        return authService.resetPassword(resetPasswordRequest);
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<String> refreshToken(@RequestBody RefreshTokenRequest refreshTokenRequest)
            throws InvalidTokenException {
        return authService.refreshToken(refreshTokenRequest);
    }

    @PostMapping("/token/revoke")
    public ResponseEntity<String> revokeRefreshToken(@RequestBody RefreshTokenRequest refreshTokenRequest) {
        return authService.revokeRefreshToken(refreshTokenRequest);
    }

}
