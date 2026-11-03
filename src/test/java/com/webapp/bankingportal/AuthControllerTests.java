package com.webapp.bankingportal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.webapp.bankingportal.dto.OtpRequest;
import com.webapp.bankingportal.dto.OtpVerificationRequest;
import com.webapp.bankingportal.dto.ResetPasswordRequest;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;

import lombok.val;

public class AuthControllerTests extends BaseTest {

    @Test
    public void test_send_password_reset_otp_response_is_identical_for_known_and_unknown_identifiers()
            throws Exception {
        val user = createAndRegisterUser();
        val accountNumber = userRepository.findByEmail(user.getEmail()).get().getAccount().getAccountNumber();

        val knownResult = mockMvc.perform(MockMvcRequestBuilders
                .post("/api/auth/password-reset/send-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new OtpRequest(accountNumber))))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

        val unknownResult = mockMvc.perform(MockMvcRequestBuilders
                .post("/api/auth/password-reset/send-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new OtpRequest(getRandomAccountNumber()))))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

        val knownBody = knownResult.getResponse().getContentAsString();
        Assertions.assertEquals(ApiMessages.OTP_SENT_SUCCESS.getMessage(), knownBody);
        Assertions.assertEquals(knownBody, unknownResult.getResponse().getContentAsString());
        Assertions.assertFalse(knownBody.contains(user.getEmail()));
    }

    @Test
    public void test_verify_password_reset_otp_with_unknown_identifier_matches_invalid_otp_response()
            throws Exception {
        val user = createAndRegisterUser();

        val knownResult = mockMvc.perform(MockMvcRequestBuilders
                .post("/api/auth/password-reset/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new OtpVerificationRequest(user.getEmail(), getRandomOtp()))))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andReturn();

        val unknownResult = mockMvc.perform(MockMvcRequestBuilders
                .post("/api/auth/password-reset/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new OtpVerificationRequest(createUser().getEmail(), getRandomOtp()))))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andReturn();

        Assertions.assertEquals(ApiMessages.OTP_INVALID_ERROR.getMessage(),
                unknownResult.getResponse().getContentAsString());
        Assertions.assertEquals(knownResult.getResponse().getContentAsString(),
                unknownResult.getResponse().getContentAsString());
    }

    @Test
    public void test_reset_password_with_unknown_identifier_matches_invalid_token_response() throws Exception {
        val user = createAndRegisterUser();

        val knownResult = mockMvc.perform(MockMvcRequestBuilders
                .post("/api/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new ResetPasswordRequest(user.getEmail(), "bogus", getRandomPassword()))))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andReturn();

        val unknownResult = mockMvc.perform(MockMvcRequestBuilders
                .post("/api/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(
                        new ResetPasswordRequest(createUser().getEmail(), "bogus", getRandomPassword()))))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andReturn();

        Assertions.assertEquals(ApiMessages.TOKEN_INVALID_ERROR.getMessage(),
                unknownResult.getResponse().getContentAsString());
        Assertions.assertEquals(knownResult.getResponse().getContentAsString(),
                unknownResult.getResponse().getContentAsString());
    }
}
