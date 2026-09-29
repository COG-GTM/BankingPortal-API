package com.webapp.bankingportal.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.webapp.bankingportal.dto.LoginRequest;
import com.webapp.bankingportal.dto.OtpVerificationRequest;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.exception.UnauthorizedException;
import com.webapp.bankingportal.exception.UserInvalidException;
import com.webapp.bankingportal.mapper.UserMapper;
import com.webapp.bankingportal.repository.UserRepository;
import com.webapp.bankingportal.service.AccountService;
import com.webapp.bankingportal.service.EmailService;
import com.webapp.bankingportal.service.GeolocationService;
import com.webapp.bankingportal.service.OtpService;
import com.webapp.bankingportal.service.TokenService;
import com.webapp.bankingportal.service.UserServiceImpl;
import com.webapp.bankingportal.util.ValidationUtil;

import lombok.val;

class UserServiceAuditLoggingTest {

    private static final long USER_ID = 42L;
    private static final String EMAIL = "jane.doe@example.com";
    private static final String NAME = "Jane Doe";
    private static final String PHONE = "+14155550123";
    private static final String ADDRESS = "221B Baker Street";
    private static final String ACCOUNT_NUMBER = "a7f3c9";
    private static final String PASSWORD = "Sup3r-Secret!Pass";
    private static final String OTP = "482913";
    private static final String TOKEN = "eyJhbGciOiJIUzUxMiJ9.test-token-payload.test-signature";
    private static final String REMOTE_IP = "203.0.113.7";
    private static final String UNKNOWN_IDENTIFIER = "nobody@example.org";

    private static final String[] SENSITIVE = {
            EMAIL, NAME, PHONE, ADDRESS, ACCOUNT_NUMBER, PASSWORD, OTP, TOKEN, REMOTE_IP, UNKNOWN_IDENTIFIER };

    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final EmailService emailService = mock(EmailService.class);
    private final GeolocationService geolocationService = mock(GeolocationService.class);
    private final OtpService otpService = mock(OtpService.class);
    private final TokenService tokenService = mock(TokenService.class);
    private final UserDetailsService userDetailsService = mock(UserDetailsService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ValidationUtil validationUtil = mock(ValidationUtil.class);

    private UserServiceImpl userService;
    private LogCapture logs;

    @BeforeEach
    void setUp() throws Exception {
        userService = new UserServiceImpl(
                mock(AccountService.class), authenticationManager, emailService, geolocationService, otpService,
                mock(PasswordEncoder.class), new SecurityAuditLogger(), tokenService, userDetailsService,
                mock(UserMapper.class), userRepository, validationUtil);

        val user = new User();
        user.setId(USER_ID);
        user.setName(NAME);
        user.setEmail(EMAIL);
        user.setPhoneNumber(PHONE);
        user.setAddress(ADDRESS);
        val account = new Account();
        account.setAccountNumber(ACCOUNT_NUMBER);
        account.setUser(user);
        user.setAccount(account);

        when(validationUtil.doesEmailExist(EMAIL)).thenReturn(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(userDetailsService.loadUserByUsername(ACCOUNT_NUMBER)).thenReturn(
                org.springframework.security.core.userdetails.User.withUsername(ACCOUNT_NUMBER)
                        .password("hash").build());
        when(tokenService.generateToken(any())).thenReturn(TOKEN);
        when(geolocationService.getGeolocation(anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));
        when(emailService.getLoginEmailTemplate(anyString(), anyString(), anyString())).thenReturn("body");
        when(emailService.sendEmail(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        logs = new LogCapture();
    }

    @AfterEach
    void tearDown() {
        logs.close();
    }

    private static MockHttpServletRequest request() {
        val request = new MockHttpServletRequest();
        request.setRemoteAddr(REMOTE_IP);
        return request;
    }

    @Test
    void passwordLoginSuccess_isAuditedWithoutSensitiveData() throws Exception {
        userService.login(new LoginRequest(EMAIL, PASSWORD), request());

        assertThat(LogCapture.keyValues(logs.singleAuditEvent()))
                .containsEntry("event", "LOGIN_SUCCESS")
                .containsEntry("auth_method", "PASSWORD")
                .containsEntry("user_id", String.valueOf(USER_ID));
        logs.assertNoneContain(SENSITIVE);
    }

    @Test
    void passwordLoginBadCredentials_isAuditedWithoutSensitiveData() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> userService.login(new LoginRequest(EMAIL, PASSWORD), request()))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(LogCapture.keyValues(logs.singleAuditEvent()))
                .containsEntry("event", "LOGIN_FAILURE")
                .containsEntry("outcome", "FAILURE")
                .containsEntry("reason", "BAD_CREDENTIALS")
                .containsEntry("user_id", String.valueOf(USER_ID));
        logs.assertNoneContain(SENSITIVE);
    }

    @Test
    void loginWithUnknownIdentifier_isAuditedWithoutEchoingIdentifier() {
        assertThatThrownBy(() -> userService.login(new LoginRequest(UNKNOWN_IDENTIFIER, PASSWORD), request()))
                .isInstanceOf(UserInvalidException.class);

        assertThat(LogCapture.keyValues(logs.singleAuditEvent()))
                .containsEntry("event", "LOGIN_FAILURE")
                .containsEntry("reason", "UNKNOWN_IDENTIFIER")
                .doesNotContainKey("user_id");
        logs.assertNoneContain(SENSITIVE);
    }

    @Test
    void otpLoginSuccess_isAuditedWithoutSensitiveData() throws Exception {
        when(otpService.validateOTP(ACCOUNT_NUMBER, OTP)).thenReturn(true);

        userService.verifyOtpAndLogin(new OtpVerificationRequest(EMAIL, OTP));

        assertThat(LogCapture.keyValues(logs.singleAuditEvent()))
                .containsEntry("event", "LOGIN_SUCCESS")
                .containsEntry("auth_method", "OTP");
        logs.assertNoneContain(SENSITIVE);
    }

    @Test
    void otpLoginWithInvalidOtp_isAuditedWithoutSensitiveData() {
        when(otpService.validateOTP(ACCOUNT_NUMBER, OTP)).thenReturn(false);

        assertThatThrownBy(() -> userService.verifyOtpAndLogin(new OtpVerificationRequest(EMAIL, OTP)))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(LogCapture.keyValues(logs.singleAuditEvent()))
                .containsEntry("event", "LOGIN_FAILURE")
                .containsEntry("auth_method", "OTP")
                .containsEntry("reason", "INVALID_OTP");
        logs.assertNoneContain(SENSITIVE);
    }

    @Test
    void logout_revokesTokenAsLogoutWithoutLoggingIt() throws Exception {
        userService.logout("Bearer " + TOKEN);

        verify(tokenService).invalidateToken(TOKEN, TokenRevocationReason.LOGOUT);
        logs.assertNoneContain(SENSITIVE);
    }

}
