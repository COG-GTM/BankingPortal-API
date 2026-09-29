package com.webapp.bankingportal.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.webapp.bankingportal.dto.OtpRequest;
import com.webapp.bankingportal.dto.OtpVerificationRequest;
import com.webapp.bankingportal.dto.RefreshTokenRequest;
import com.webapp.bankingportal.dto.ResetPasswordRequest;
import com.webapp.bankingportal.exception.InvalidTokenException;
import com.webapp.bankingportal.service.AuthService;
import com.webapp.bankingportal.service.RefreshTokenService;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.RequiredArgsConstructor;
import lombok.val;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/refresh")
    public ResponseEntity<String> refresh(@RequestBody RefreshTokenRequest refreshTokenRequest)
            throws InvalidTokenException {
        val tokenPair = refreshTokenService.rotate(refreshTokenRequest.refreshToken());
        return ResponseEntity.ok(String.format(ApiMessages.TOKEN_PAIR_ISSUED_SUCCESS.getMessage(),
                tokenPair.accessToken(), tokenPair.refreshToken()));
    }

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

}
