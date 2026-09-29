package com.webapp.bankingportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.webapp.bankingportal.dto.LoginRequest;
import com.webapp.bankingportal.repository.LoginAttemptRepository;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;

import lombok.val;

@TestPropertySource(properties = {
        "security.login-lockout.max-attempts=3",
        "security.login-lockout.base-lock-duration=60s",
        "security.login-lockout.backoff-multiplier=2",
        "security.login-lockout.max-lock-duration=10m",
        "security.login-lockout.failure-reset-window=1h"
})
public class LoginLockoutTests extends BaseTest {

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        }
    }

    @Autowired
    private MutableClock clock;

    @Autowired
    private Clock injectedClock;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    private String accountNumber;
    private String password;

    @BeforeEach
    void registerUser() throws Exception {
        clock.advance(Duration.ofDays(1));
        val user = createAndRegisterUser();
        accountNumber = userRepository.findByEmail(user.getEmail()).get().getAccount().getAccountNumber();
        password = user.getPassword();
    }

    private ResultActions login(String pwd) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders
                .post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new LoginRequest(accountNumber, pwd))));
    }

    private void failLogins(int count) throws Exception {
        for (int i = 0; i < count; i++) {
            login(getRandomPassword()).andExpect(MockMvcResultMatchers.status().isUnauthorized());
        }
    }

    private void expectLocked(long retryAfterSeconds) throws Exception {
        login(password)
                .andExpect(MockMvcResultMatchers.status().isTooManyRequests())
                .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.RETRY_AFTER,
                        String.valueOf(retryAfterSeconds)))
                .andExpect(MockMvcResultMatchers.content().string(
                        String.format(ApiMessages.ACCOUNT_LOCKED.getMessage(), retryAfterSeconds)));
    }

    @Test
    public void test_uses_injected_clock() {
        assertSame(clock, injectedClock);
    }

    @Test
    public void test_account_locked_after_max_failed_attempts_even_with_correct_password() throws Exception {
        failLogins(3);
        expectLocked(60);
    }

    @Test
    public void test_login_allowed_after_lock_expires() throws Exception {
        failLogins(3);
        clock.advance(Duration.ofSeconds(60));
        login(password).andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    public void test_lock_duration_backs_off_exponentially() throws Exception {
        failLogins(3);
        expectLocked(60);

        clock.advance(Duration.ofSeconds(60));
        failLogins(1);
        expectLocked(120);

        clock.advance(Duration.ofSeconds(120));
        failLogins(1);
        expectLocked(240);
    }

    @Test
    public void test_successful_login_resets_failure_count() throws Exception {
        failLogins(2);
        login(password).andExpect(MockMvcResultMatchers.status().isOk());
        assertTrue(loginAttemptRepository.findByAccountNumber(accountNumber).isEmpty());

        failLogins(2);
        assertEquals(2, loginAttemptRepository.findByAccountNumber(accountNumber).get().getFailedAttempts());
    }

    @Test
    public void test_attempts_while_locked_do_not_extend_lock() throws Exception {
        failLogins(3);
        clock.advance(Duration.ofSeconds(30));
        login(getRandomPassword()).andExpect(MockMvcResultMatchers.status().isTooManyRequests());
        expectLocked(30);
    }
}
