package com.webapp.bankingportal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.webapp.bankingportal.dto.OtpRequest;
import com.webapp.bankingportal.dto.OtpVerificationRequest;
import com.webapp.bankingportal.dto.ResetPasswordRequest;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.PasswordResetToken;
import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.exception.PasswordResetException;
import com.webapp.bankingportal.repository.PasswordResetTokenRepository;
import com.webapp.bankingportal.util.ApiMessages;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplUnitTests {

    private static final String ACCOUNT_NUMBER = "abc123";
    private static final String EMAIL = "jane@example.com";

    @Mock
    private OtpService otpService;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private UserService userService;

    private AuthServiceImpl authService;

    private User user;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(otpService, passwordResetTokenRepository, userService);

        user = new User();
        user.setId(1L);
        user.setName("Jane");
        user.setEmail(EMAIL);
        var account = new Account();
        account.setAccountNumber(ACCOUNT_NUMBER);
        user.setAccount(account);
    }

    @Test
    void generatePasswordResetToken_reusesStillValidToken() {
        when(passwordResetTokenRepository.findByUser(user))
                .thenReturn(new PasswordResetToken("existing", user, LocalDateTime.now().plusHours(1)));

        assertEquals("existing", authService.generatePasswordResetToken(user));
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void generatePasswordResetToken_issuesNewTokenWhenExistingIsAboutToExpire() {
        when(passwordResetTokenRepository.findByUser(user))
                .thenReturn(new PasswordResetToken("existing", user, LocalDateTime.now().plusMinutes(1)));

        var token = authService.generatePasswordResetToken(user);

        assertNotEquals("existing", token);
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void generatePasswordResetToken_issuesNewTokenExpiringIn24Hours() {
        var token = authService.generatePasswordResetToken(user);

        var captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(captor.capture());
        assertEquals(token, captor.getValue().getToken());
        assertEquals(user, captor.getValue().getUser());
        var expiry = captor.getValue().getExpiryDateTime();
        assertTrue(expiry.isAfter(LocalDateTime.now().plusHours(23)));
        assertTrue(expiry.isBefore(LocalDateTime.now().plusHours(25)));
    }

    @Test
    void verifyPasswordResetToken_returnsFalseForUnknownToken() {
        when(passwordResetTokenRepository.findByToken("unknown")).thenReturn(Optional.empty());

        assertFalse(authService.verifyPasswordResetToken("unknown", user));
        verify(passwordResetTokenRepository, never()).deleteByToken(any());
    }

    @Test
    void verifyPasswordResetToken_consumesValidTokenForSameUser() {
        when(passwordResetTokenRepository.findByToken("tok"))
                .thenReturn(Optional.of(new PasswordResetToken("tok", user, LocalDateTime.now().plusHours(1))));

        assertTrue(authService.verifyPasswordResetToken("tok", user));
        verify(passwordResetTokenRepository).deleteByToken("tok");
    }

    @Test
    void verifyPasswordResetToken_rejectsTokenOfAnotherUser() {
        var other = new User();
        other.setId(2L);
        when(passwordResetTokenRepository.findByToken("tok"))
                .thenReturn(Optional.of(new PasswordResetToken("tok", other, LocalDateTime.now().plusHours(1))));

        assertFalse(authService.verifyPasswordResetToken("tok", user));
        verify(passwordResetTokenRepository).deleteByToken("tok");
    }

    @Test
    void verifyPasswordResetToken_rejectsExpiredToken() {
        when(passwordResetTokenRepository.findByToken("tok"))
                .thenReturn(Optional.of(new PasswordResetToken("tok", user, LocalDateTime.now().minusMinutes(1))));

        assertFalse(authService.verifyPasswordResetToken("tok", user));
    }

    @Test
    void sendOtpForPasswordReset_returnsOkWhenEmailSent() {
        when(userService.getUserByIdentifier(EMAIL)).thenReturn(user);
        when(otpService.generateOTP(ACCOUNT_NUMBER)).thenReturn("111222");
        when(otpService.sendOTPByEmail(EMAIL, "Jane", ACCOUNT_NUMBER, "111222"))
                .thenReturn(CompletableFuture.completedFuture(null));

        var response = authService.sendOtpForPasswordReset(new OtpRequest(EMAIL));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(String.format(ApiMessages.OTP_SENT_SUCCESS.getMessage(), EMAIL), response.getBody());
    }

    @Test
    void sendOtpForPasswordReset_returnsServerErrorWhenEmailFails() {
        when(userService.getUserByIdentifier(EMAIL)).thenReturn(user);
        when(otpService.generateOTP(ACCOUNT_NUMBER)).thenReturn("111222");
        when(otpService.sendOTPByEmail(EMAIL, "Jane", ACCOUNT_NUMBER, "111222"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("smtp down")));

        var response = authService.sendOtpForPasswordReset(new OtpRequest(EMAIL));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(String.format(ApiMessages.OTP_SENT_FAILURE.getMessage(), EMAIL), response.getBody());
    }

    @Test
    void verifyOtpAndIssueResetToken_rejectsMissingIdentifierOrOtp() {
        var missingId = assertThrows(IllegalArgumentException.class,
                () -> authService.verifyOtpAndIssueResetToken(new OtpVerificationRequest("", "111222")));
        assertEquals(ApiMessages.IDENTIFIER_MISSING_ERROR.getMessage(), missingId.getMessage());

        var missingOtp = assertThrows(IllegalArgumentException.class,
                () -> authService.verifyOtpAndIssueResetToken(new OtpVerificationRequest(EMAIL, null)));
        assertEquals(ApiMessages.OTP_MISSING_ERROR.getMessage(), missingOtp.getMessage());
    }

    @Test
    void verifyOtpAndIssueResetToken_returnsUnauthorizedForInvalidOtp() {
        when(userService.getUserByIdentifier(EMAIL)).thenReturn(user);
        when(otpService.validateOTP(ACCOUNT_NUMBER, "111222")).thenReturn(false);

        var response = authService.verifyOtpAndIssueResetToken(new OtpVerificationRequest(EMAIL, "111222"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(ApiMessages.OTP_INVALID_ERROR.getMessage(), response.getBody());
    }

    @Test
    void verifyOtpAndIssueResetToken_issuesTokenForValidOtp() {
        when(userService.getUserByIdentifier(EMAIL)).thenReturn(user);
        when(otpService.validateOTP(ACCOUNT_NUMBER, "111222")).thenReturn(true);
        when(passwordResetTokenRepository.findByUser(user))
                .thenReturn(new PasswordResetToken("existing", user, LocalDateTime.now().plusHours(1)));

        var response = authService.verifyOtpAndIssueResetToken(new OtpVerificationRequest(EMAIL, "111222"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(String.format(ApiMessages.PASSWORD_RESET_TOKEN_ISSUED.getMessage(), "existing"),
                response.getBody());
    }

    private void givenValidResetToken() {
        when(userService.getUserByIdentifier(EMAIL)).thenReturn(user);
        when(passwordResetTokenRepository.findByToken("tok"))
                .thenReturn(Optional.of(new PasswordResetToken("tok", user, LocalDateTime.now().plusHours(1))));
    }

    @Test
    void resetPassword_returnsUnauthorizedForInvalidToken() {
        when(userService.getUserByIdentifier(EMAIL)).thenReturn(user);
        when(passwordResetTokenRepository.findByToken("tok")).thenReturn(Optional.empty());

        var response = authService.resetPassword(new ResetPasswordRequest(EMAIL, "tok", "NewPass!1"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(ApiMessages.TOKEN_INVALID_ERROR.getMessage(), response.getBody());
        verify(userService, never()).resetPassword(any(), any());
    }

    @Test
    void resetPassword_returnsOkWhenPasswordUpdated() {
        givenValidResetToken();
        when(userService.resetPassword(user, "NewPass!1")).thenReturn(true);

        var response = authService.resetPassword(new ResetPasswordRequest(EMAIL, "tok", "NewPass!1"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ApiMessages.PASSWORD_RESET_SUCCESS.getMessage(), response.getBody());
    }

    @Test
    void resetPassword_returnsServerErrorWhenUpdateReportsFailure() {
        givenValidResetToken();
        when(userService.resetPassword(user, "NewPass!1")).thenReturn(false);

        var response = authService.resetPassword(new ResetPasswordRequest(EMAIL, "tok", "NewPass!1"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(ApiMessages.PASSWORD_RESET_FAILURE.getMessage(), response.getBody());
    }

    @Test
    void resetPassword_returnsServerErrorWhenUpdateThrows() {
        givenValidResetToken();
        when(userService.resetPassword(user, "NewPass!1"))
                .thenThrow(new PasswordResetException("boom", new RuntimeException()));

        var response = authService.resetPassword(new ResetPasswordRequest(EMAIL, "tok", "NewPass!1"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(ApiMessages.PASSWORD_RESET_FAILURE.getMessage(), response.getBody());
    }
}
