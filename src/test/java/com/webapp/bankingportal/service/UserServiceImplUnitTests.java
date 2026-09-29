package com.webapp.bankingportal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.core.userdetails.User.withUsername;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.webapp.bankingportal.dto.GeolocationResponse;
import com.webapp.bankingportal.dto.LoginRequest;
import com.webapp.bankingportal.dto.OtpRequest;
import com.webapp.bankingportal.dto.OtpVerificationRequest;
import com.webapp.bankingportal.entity.Account;
import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.exception.InvalidTokenException;
import com.webapp.bankingportal.exception.PasswordResetException;
import com.webapp.bankingportal.exception.UnauthorizedException;
import com.webapp.bankingportal.exception.UserInvalidException;
import com.webapp.bankingportal.mapper.UserMapper;
import com.webapp.bankingportal.repository.UserRepository;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.ValidationUtil;

@ExtendWith(MockitoExtension.class)
class UserServiceImplUnitTests {

    private static final String ACCOUNT_NUMBER = "abc123";
    private static final String EMAIL = "jane@example.com";
    private static final String PASSWORD = "Str0ng!Pass";
    private static final String TOKEN = "jwt-token";

    @Mock
    private AccountService accountService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private EmailService emailService;
    @Mock
    private GeolocationService geolocationService;
    @Mock
    private OtpService otpService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ValidationUtil validationUtil;

    private UserServiceImpl userService;

    private User user;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(accountService, authenticationManager, emailService, geolocationService,
                otpService, passwordEncoder, tokenService, userDetailsService, userMapper, userRepository,
                validationUtil);

        user = new User();
        user.setName("Jane");
        user.setEmail(EMAIL);
        user.setPassword("encoded");
        var account = new Account();
        account.setAccountNumber(ACCOUNT_NUMBER);
        account.setUser(user);
        user.setAccount(account);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void givenUserFoundByEmail() {
        when(validationUtil.doesEmailExist(EMAIL)).thenReturn(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    private void givenTokenIssued() throws InvalidTokenException {
        var userDetails = withUsername(ACCOUNT_NUMBER).password("encoded").build();
        when(userDetailsService.loadUserByUsername(ACCOUNT_NUMBER)).thenReturn(userDetails);
        when(tokenService.generateToken(userDetails)).thenReturn(TOKEN);
    }

    private static GeolocationResponse geolocation(String city, String country) {
        var cityInfo = new GeolocationResponse.City();
        cityInfo.setNames(Map.of("en", city));
        var countryInfo = new GeolocationResponse.Country();
        countryInfo.setNames(Map.of("en", country));
        var response = new GeolocationResponse();
        response.setCity(cityInfo);
        response.setCountry(countryInfo);
        return response;
    }

    @Test
    void registerUser_encodesPasswordAndCreatesAccount() {
        var newUser = new User();
        newUser.setName("Jane");
        newUser.setEmail(EMAIL);
        newUser.setCountryCode("us");
        newUser.setPassword(PASSWORD);
        var account = new Account();
        account.setAccountNumber(ACCOUNT_NUMBER);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded");
        when(userRepository.save(newUser)).thenReturn(newUser);
        when(accountService.createAccount(newUser)).thenReturn(account);

        var response = userService.registerUser(newUser);

        verify(validationUtil).validateNewUser(newUser);
        assertEquals("US", newUser.getCountryCode());
        assertEquals("encoded", newUser.getPassword());
        assertSame(account, newUser.getAccount());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"accountNumber\":\"" + ACCOUNT_NUMBER + "\""));
    }

    @Test
    void login_authenticatesIssuesTokenAndSendsLocationEmail() throws InvalidTokenException {
        givenUserFoundByEmail();
        givenTokenIssued();
        when(geolocationService.getGeolocation("10.0.0.1"))
                .thenReturn(CompletableFuture.completedFuture(geolocation("Paris", "France")));
        when(emailService.getLoginEmailTemplate(eq("Jane"), anyString(), eq("Paris, France"))).thenReturn("<html/>");
        when(emailService.sendEmail(EMAIL, ApiMessages.EMAIL_SUBJECT_LOGIN.getMessage(), "<html/>"))
                .thenReturn(CompletableFuture.completedFuture(null));
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");

        var response = userService.login(new LoginRequest(EMAIL, PASSWORD), request);

        assertEquals(String.format(ApiMessages.TOKEN_ISSUED_SUCCESS.getMessage(), TOKEN), response.getBody());
        var auth = ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(auth.capture());
        assertEquals(ACCOUNT_NUMBER, auth.getValue().getPrincipal());
        assertEquals(PASSWORD, auth.getValue().getCredentials());
        verify(tokenService).saveToken(TOKEN);
        verify(emailService, timeout(2000)).sendEmail(EMAIL, ApiMessages.EMAIL_SUBJECT_LOGIN.getMessage(),
                "<html/>");
    }

    @Test
    void login_fallsBackToUnknownLocationWhenGeolocationFails() throws InvalidTokenException {
        givenUserFoundByEmail();
        givenTokenIssued();
        when(geolocationService.getGeolocation("10.0.0.1"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("geo down")));
        when(emailService.getLoginEmailTemplate(eq("Jane"), anyString(), eq("Unknown"))).thenReturn("<html/>");
        when(emailService.sendEmail(EMAIL, ApiMessages.EMAIL_SUBJECT_LOGIN.getMessage(), "<html/>"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("smtp down")));
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");

        var response = userService.login(new LoginRequest(EMAIL, PASSWORD), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(emailService, timeout(2000)).getLoginEmailTemplate(eq("Jane"), anyString(), eq("Unknown"));
    }

    @Test
    void login_propagatesAuthenticationFailure() throws InvalidTokenException {
        givenUserFoundByEmail();
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThrows(BadCredentialsException.class,
                () -> userService.login(new LoginRequest(EMAIL, "wrong"), new MockHttpServletRequest()));
        verify(tokenService, never()).saveToken(anyString());
    }

    @Test
    void generateOtp_returnsOkWhenEmailSent() {
        givenUserFoundByEmail();
        when(otpService.generateOTP(ACCOUNT_NUMBER)).thenReturn("111222");
        when(otpService.sendOTPByEmail(EMAIL, "Jane", ACCOUNT_NUMBER, "111222"))
                .thenReturn(CompletableFuture.completedFuture(null));

        var response = userService.generateOtp(new OtpRequest(EMAIL));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(String.format(ApiMessages.OTP_SENT_SUCCESS.getMessage(), EMAIL), response.getBody());
    }

    @Test
    void generateOtp_returnsServerErrorWhenEmailFails() {
        givenUserFoundByEmail();
        when(otpService.generateOTP(ACCOUNT_NUMBER)).thenReturn("111222");
        when(otpService.sendOTPByEmail(EMAIL, "Jane", ACCOUNT_NUMBER, "111222"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("smtp down")));

        var response = userService.generateOtp(new OtpRequest(EMAIL));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(String.format(ApiMessages.OTP_SENT_FAILURE.getMessage(), EMAIL), response.getBody());
    }

    @Test
    void verifyOtpAndLogin_rejectsMissingIdentifierOrOtp() {
        var missingId = assertThrows(IllegalArgumentException.class,
                () -> userService.verifyOtpAndLogin(new OtpVerificationRequest(null, "111222")));
        assertEquals(ApiMessages.IDENTIFIER_MISSING_ERROR.getMessage(), missingId.getMessage());

        var missingOtp = assertThrows(IllegalArgumentException.class,
                () -> userService.verifyOtpAndLogin(new OtpVerificationRequest(EMAIL, "")));
        assertEquals(ApiMessages.OTP_MISSING_ERROR.getMessage(), missingOtp.getMessage());
    }

    @Test
    void verifyOtpAndLogin_rejectsInvalidOtp() {
        givenUserFoundByEmail();
        when(otpService.validateOTP(ACCOUNT_NUMBER, "111222")).thenReturn(false);

        var ex = assertThrows(UnauthorizedException.class,
                () -> userService.verifyOtpAndLogin(new OtpVerificationRequest(EMAIL, "111222")));
        assertEquals(ApiMessages.OTP_INVALID_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    void verifyOtpAndLogin_issuesTokenForValidOtp() throws InvalidTokenException {
        givenUserFoundByEmail();
        givenTokenIssued();
        when(otpService.validateOTP(ACCOUNT_NUMBER, "111222")).thenReturn(true);

        var response = userService.verifyOtpAndLogin(new OtpVerificationRequest(EMAIL, "111222"));

        assertEquals(String.format(ApiMessages.TOKEN_ISSUED_SUCCESS.getMessage(), TOKEN), response.getBody());
        verify(tokenService).saveToken(TOKEN);
    }

    @Test
    void updateUser_appliesChangesForLoggedInUserKeepingPassword() {
        var principal = withUsername(ACCOUNT_NUMBER).password("encoded").authorities(List.of()).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(userRepository.findByAccountAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        var phoneUtil = PhoneNumberUtil.getInstance();
        var updated = new User();
        updated.setName("Jane Updated");
        updated.setEmail("jane.updated@example.com");
        updated.setAddress("1 Main St");
        updated.setCountryCode("US");
        updated.setPhoneNumber(phoneUtil.format(phoneUtil.getExampleNumber("US"),
                PhoneNumberUtil.PhoneNumberFormat.E164));
        updated.setPassword(PASSWORD);

        var response = userService.updateUser(updated);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        assertEquals("encoded", updated.getPassword());
        verify(userMapper).updateUser(updated, user);
        verify(userRepository).save(user);
    }

    @Test
    void resetPassword_encodesAndSavesNewPassword() {
        when(passwordEncoder.encode("NewPass!1")).thenReturn("new-encoded");

        assertTrue(userService.resetPassword(user, "NewPass!1"));
        assertEquals("new-encoded", user.getPassword());
        verify(userRepository).save(user);
    }

    @Test
    void resetPassword_wrapsPersistenceFailure() {
        when(passwordEncoder.encode("NewPass!1")).thenReturn("new-encoded");
        when(userRepository.save(user)).thenThrow(new RuntimeException("db down"));

        var ex = assertThrows(PasswordResetException.class, () -> userService.resetPassword(user, "NewPass!1"));
        assertEquals(ApiMessages.PASSWORD_RESET_FAILURE.getMessage(), ex.getMessage());
    }

    @Test
    void logout_invalidatesBearerTokenAndRedirects() throws InvalidTokenException {
        when(tokenService.getUsernameFromToken(TOKEN)).thenReturn(ACCOUNT_NUMBER);

        var view = userService.logout("Bearer " + TOKEN);

        verify(tokenService).validateToken(TOKEN);
        verify(tokenService).invalidateToken(TOKEN);
        assertEquals("redirect:/logout", view.getViewName());
    }

    @Test
    void getUserByIdentifier_resolvesByAccountNumber() {
        when(validationUtil.doesEmailExist(ACCOUNT_NUMBER)).thenReturn(false);
        when(validationUtil.doesAccountExist(ACCOUNT_NUMBER)).thenReturn(true);
        when(userRepository.findByAccountAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(user));

        assertSame(user, userService.getUserByIdentifier(ACCOUNT_NUMBER));
    }

    @Test
    void getUserByIdentifier_rejectsUnknownIdentifier() {
        var ex = assertThrows(UserInvalidException.class, () -> userService.getUserByIdentifier("nobody"));
        assertEquals(String.format(ApiMessages.USER_NOT_FOUND_BY_IDENTIFIER.getMessage(), "nobody"),
                ex.getMessage());
    }

    @Test
    void getUserByAccountNumber_rejectsUnknownAccount() {
        when(userRepository.findByAccountAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.empty());

        var ex = assertThrows(UserInvalidException.class, () -> userService.getUserByAccountNumber(ACCOUNT_NUMBER));
        assertEquals(String.format(ApiMessages.USER_NOT_FOUND_BY_ACCOUNT.getMessage(), ACCOUNT_NUMBER),
                ex.getMessage());
    }

    @Test
    void getUserByEmail_rejectsUnknownEmail() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        var ex = assertThrows(UserInvalidException.class, () -> userService.getUserByEmail(EMAIL));
        assertEquals(String.format(ApiMessages.USER_NOT_FOUND_BY_EMAIL.getMessage(), EMAIL), ex.getMessage());
    }
}
