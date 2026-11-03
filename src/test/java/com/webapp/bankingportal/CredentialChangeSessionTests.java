package com.webapp.bankingportal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.jayway.jsonpath.JsonPath;
import com.webapp.bankingportal.dto.LoginRequest;
import com.webapp.bankingportal.dto.PinUpdateRequest;
import com.webapp.bankingportal.dto.ResetPasswordRequest;
import com.webapp.bankingportal.service.AuthService;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;

import lombok.val;

public class CredentialChangeSessionTests extends BaseTest {

    @Autowired
    private AuthService authService;

    private void expectDashboardStatus(String token, int status) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/dashboard/user")
                .header("Authorization", "Bearer " + token))
                .andExpect(MockMvcResultMatchers.status().is(status));
    }

    private String login(String accountNumber, String password) throws Exception {
        val result = mockMvc.perform(MockMvcRequestBuilders
                .post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new LoginRequest(accountNumber, password))))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private String issueResetToken(String accountNumber) {
        val user = userRepository.findByAccountAccountNumber(accountNumber).get();
        return authService.generatePasswordResetToken(user);
    }

    @Test
    public void test_password_reset_revokes_existing_sessions() throws Exception {
        val userDetails = createAndLoginUser();
        val accountNumber = userDetails.get("accountNumber");
        val oldToken = userDetails.get("token");
        expectDashboardStatus(oldToken, 200);

        val newPassword = getRandomPassword();
        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new ResetPasswordRequest(
                        accountNumber, issueResetToken(accountNumber), newPassword))))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.PASSWORD_RESET_SUCCESS.getMessage()));

        expectDashboardStatus(oldToken, 401);
        expectDashboardStatus(login(accountNumber, newPassword), 200);
    }

    @Test
    public void test_password_reset_with_weak_password_is_rejected() throws Exception {
        val userDetails = createAndLoginUser();
        val accountNumber = userDetails.get("accountNumber");
        val oldToken = userDetails.get("token");

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new ResetPasswordRequest(
                        accountNumber, issueResetToken(accountNumber), "a"))))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.PASSWORD_TOO_SHORT_ERROR.getMessage()));

        expectDashboardStatus(oldToken, 200);
        login(accountNumber, userDetails.get("password"));
    }

    @Test
    public void test_pin_update_revokes_other_sessions() throws Exception {
        val userDetails = createAndLoginUserWithPin();
        val accountNumber = userDetails.get("accountNumber");
        val currentToken = userDetails.get("token");
        val otherToken = login(accountNumber, userDetails.get("password"));
        expectDashboardStatus(otherToken, 200);

        mockMvc.perform(MockMvcRequestBuilders
                .post("/api/account/pin/update")
                .header("Authorization", "Bearer " + currentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new PinUpdateRequest(accountNumber,
                        userDetails.get("pin"), getRandomPin(), userDetails.get("password")))))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.PIN_UPDATE_SUCCESS.getMessage()));

        expectDashboardStatus(otherToken, 401);
        expectDashboardStatus(currentToken, 200);
    }

}
