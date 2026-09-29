package com.webapp.bankingportal.ratelimit;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.webapp.bankingportal.controller.AuthController;
import com.webapp.bankingportal.controller.UserController;
import com.webapp.bankingportal.service.AuthService;
import com.webapp.bankingportal.service.UserService;

class RateLimitFilterTest {

    private static final String LOGIN = "/api/users/login";
    private static final String VERIFY_OTP_LOGIN = "/api/users/verify-otp";
    private static final String GENERATE_OTP = "/api/users/generate-otp";
    private static final String RESET_SEND_OTP = "/api/auth/password-reset/send-otp";
    private static final String RESET_VERIFY_OTP = "/api/auth/password-reset/verify-otp";
    private static final String RESET_PASSWORD = "/api/auth/password-reset";

    private MutableClock clock;
    private RateLimitProperties properties;
    private UserService userService;
    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        clock = new MutableClock();
        properties = new RateLimitProperties();
        properties.setLogin(new RateLimitProperties.Policy(3, Duration.ofMinutes(1)));
        properties.setOtp(new RateLimitProperties.Policy(2, Duration.ofMinutes(5)));
        properties.setPasswordReset(new RateLimitProperties.Policy(2, Duration.ofMinutes(15)));

        userService = mock(UserService.class);
        authService = mock(AuthService.class);
        when(userService.login(any(), any())).thenReturn(ResponseEntity.ok("{}"));
        when(userService.verifyOtpAndLogin(any())).thenReturn(ResponseEntity.ok("{}"));
        when(userService.generateOtp(any())).thenReturn(ResponseEntity.ok("{}"));
        when(authService.sendOtpForPasswordReset(any())).thenReturn(ResponseEntity.ok("{}"));
        when(authService.verifyOtpAndIssueResetToken(any())).thenReturn(ResponseEntity.ok("{}"));
        when(authService.resetPassword(any())).thenReturn(ResponseEntity.ok("{}"));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService), new AuthController(authService))
                .addFilters(new RateLimitFilter(properties, clock))
                .build();
    }

    private static MockHttpServletRequestBuilder postJson(String path, String ip) {
        return post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"user@example.com\",\"password\":\"x\",\"otp\":\"123456\"}")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                });
    }

    private void exhaust(String path, int times, String ip) throws Exception {
        for (int i = 0; i < times; i++) {
            mockMvc.perform(postJson(path, ip)).andExpect(status().isOk());
        }
    }

    @Test
    void loginReturns429WithClearResponseOnceLimitExceeded() throws Exception {
        mockMvc.perform(postJson(LOGIN, "10.0.0.1"))
                .andExpect(status().isOk())
                .andExpect(header().string(RateLimitFilter.HEADER_LIMIT, "3"))
                .andExpect(header().string(RateLimitFilter.HEADER_REMAINING, "2"));
        exhaust(LOGIN, 2, "10.0.0.1");

        mockMvc.perform(postJson(LOGIN, "10.0.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(header().string(RateLimitFilter.HEADER_LIMIT, "3"))
                .andExpect(header().string(RateLimitFilter.HEADER_REMAINING, "0"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Too many requests. Please try again in 60 seconds."));

        verify(userService, times(3)).login(any(), any());
    }

    @Test
    void otpLoginSharesLoginBudget() throws Exception {
        exhaust(LOGIN, 3, "10.0.0.1");
        mockMvc.perform(postJson(VERIFY_OTP_LOGIN, "10.0.0.1")).andExpect(status().isTooManyRequests());
    }

    @Test
    void otpRequestEndpointsAreLimited() throws Exception {
        exhaust(GENERATE_OTP, 1, "10.0.0.1");
        exhaust(RESET_SEND_OTP, 1, "10.0.0.1");

        mockMvc.perform(postJson(GENERATE_OTP, "10.0.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "300"));
        mockMvc.perform(postJson(RESET_SEND_OTP, "10.0.0.1")).andExpect(status().isTooManyRequests());
    }

    @Test
    void passwordResetEndpointsAreLimited() throws Exception {
        exhaust(RESET_VERIFY_OTP, 1, "10.0.0.1");
        exhaust(RESET_PASSWORD, 1, "10.0.0.1");

        mockMvc.perform(postJson(RESET_PASSWORD, "10.0.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"));
        mockMvc.perform(postJson(RESET_VERIFY_OTP, "10.0.0.1")).andExpect(status().isTooManyRequests());
    }

    @Test
    void policiesHaveIndependentBudgets() throws Exception {
        exhaust(LOGIN, 3, "10.0.0.1");
        mockMvc.perform(postJson(LOGIN, "10.0.0.1")).andExpect(status().isTooManyRequests());

        mockMvc.perform(postJson(GENERATE_OTP, "10.0.0.1")).andExpect(status().isOk());
        mockMvc.perform(postJson(RESET_PASSWORD, "10.0.0.1")).andExpect(status().isOk());
    }

    @Test
    void limitsArePerClientIp() throws Exception {
        exhaust(LOGIN, 3, "10.0.0.1");
        mockMvc.perform(postJson(LOGIN, "10.0.0.1")).andExpect(status().isTooManyRequests());
        mockMvc.perform(postJson(LOGIN, "10.0.0.2")).andExpect(status().isOk());
    }

    @Test
    void limitResetsAfterWindow() throws Exception {
        exhaust(LOGIN, 3, "10.0.0.1");
        mockMvc.perform(postJson(LOGIN, "10.0.0.1")).andExpect(status().isTooManyRequests());

        clock.advance(Duration.ofSeconds(30));
        mockMvc.perform(postJson(LOGIN, "10.0.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.message", containsString("30 seconds")));

        clock.advance(Duration.ofSeconds(30));
        mockMvc.perform(postJson(LOGIN, "10.0.0.1")).andExpect(status().isOk());
    }

    @Test
    void forwardedForHeaderIgnoredByDefault() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(postJson(LOGIN, "10.0.0.1").header("X-Forwarded-For", "203.0.113." + i))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(postJson(LOGIN, "10.0.0.1").header("X-Forwarded-For", "203.0.113.99"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void forwardedForHeaderUsedWhenTrusted() throws Exception {
        properties.setTrustForwardedHeaders(true);
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(postJson(LOGIN, "10.0.0.1").header("X-Forwarded-For", "203.0.113.7, 10.0.0.1"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(postJson(LOGIN, "10.0.0.1").header("X-Forwarded-For", "203.0.113.7, 10.0.0.1"))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(postJson(LOGIN, "10.0.0.1").header("X-Forwarded-For", "203.0.113.8"))
                .andExpect(status().isOk());
    }

    @Test
    void disabledFilterNeverThrottles() throws Exception {
        properties.setEnabled(false);
        exhaust(LOGIN, 10, "10.0.0.1");
    }

    @Test
    void trailingSlashDoesNotBypassLimit() throws Exception {
        exhaust(LOGIN, 3, "10.0.0.1");
        mockMvc.perform(postJson(LOGIN + "/", "10.0.0.1")).andExpect(status().isTooManyRequests());
    }
}
