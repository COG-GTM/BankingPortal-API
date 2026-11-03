package com.webapp.bankingportal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.webapp.bankingportal.dto.ResetPasswordRequest;
import com.webapp.bankingportal.repository.TokenRepository;
import com.webapp.bankingportal.service.AuthService;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;

import lombok.val;

public class AuthControllerTests extends BaseTest {

    @Autowired
    AuthService authService;

    @Autowired
    TokenRepository tokenRepository;

    private String issueResetToken(String email) {
        return authService.generatePasswordResetToken(userRepository.findByEmail(email).get());
    }

    @Test
    public void test_reset_password_invalidates_existing_sessions() throws Exception {
        val userDetails = createAndLoginUser();
        val oldToken = userDetails.get("token");
        val newPassword = getRandomPassword();
        val request = new ResetPasswordRequest(userDetails.get("email"),
                issueResetToken(userDetails.get("email")), newPassword);

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(request)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.PASSWORD_RESET_SUCCESS.getMessage()));

        Assertions.assertNull(tokenRepository.findByToken(oldToken));
        Assertions.assertTrue(passwordEncoder.matches(newPassword,
                userRepository.findByEmail(userDetails.get("email")).get().getPassword()));

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/dashboard/user")
                .header("Authorization", "Bearer " + oldToken))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    public void test_reset_password_with_weak_password() throws Exception {
        val userDetails = createAndLoginUser();
        val oldToken = userDetails.get("token");
        val request = new ResetPasswordRequest(userDetails.get("email"),
                issueResetToken(userDetails.get("email")), "abc");

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(request)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.PASSWORD_TOO_SHORT_ERROR.getMessage()));

        Assertions.assertTrue(passwordEncoder.matches(userDetails.get("password"),
                userRepository.findByEmail(userDetails.get("email")).get().getPassword()));
        Assertions.assertNotNull(tokenRepository.findByToken(oldToken));
    }

    @Test
    public void test_reset_password_weak_password_does_not_consume_reset_token() throws Exception {
        val userDetails = createAndLoginUser();
        val resetToken = issueResetToken(userDetails.get("email"));

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new ResetPasswordRequest(userDetails.get("email"), resetToken, "abc"))))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new ResetPasswordRequest(userDetails.get("email"), resetToken,
                        getRandomPassword()))))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

}
