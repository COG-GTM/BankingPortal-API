package com.webapp.bankingportal;

import java.util.Date;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.webapp.bankingportal.repository.TokenRepository;
import com.webapp.bankingportal.service.UserService;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.val;

public class JwtAuthenticationTests extends BaseTest {

    @Autowired
    TokenRepository tokenRepository;

    @Autowired
    UserService userService;

    private static final String PROTECTED_URL = "/api/dashboard/user";

    @Test
    public void test_valid_token_is_accepted() throws Exception {
        val token = createAndLoginUser().get("token");

        mockMvc.perform(MockMvcRequestBuilders.get(PROTECTED_URL)
                .header("Authorization", "Bearer " + token))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    /**
     * Runs outside a test transaction so the filter's revocation of an expired
     * token executes the same way it does for a real request.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void test_expired_persisted_token_is_rejected_and_revoked() throws Exception {
        val accountDetails = createAccount();
        val token = generateToken(
                accountDetails.get("accountNumber"),
                accountDetails.get("password"),
                new Date(System.currentTimeMillis() + 1000));
        tokenService.saveToken(token);
        Assertions.assertNotNull(tokenRepository.findByToken(token));

        Thread.sleep(2000);

        mockMvc.perform(MockMvcRequestBuilders.get(PROTECTED_URL)
                .header("Authorization", "Bearer " + token))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.status()
                        .reason(ApiMessages.TOKEN_EXPIRED_ERROR.getMessage()));

        Assertions.assertNull(tokenRepository.findByToken(token));
    }

    @Test
    public void test_token_is_rejected_after_logout() throws Exception {
        val token = createAndLoginUser().get("token");

        mockMvc.perform(MockMvcRequestBuilders.get("/api/users/logout")
                .header("Authorization", "Bearer " + token))
                .andExpect(MockMvcResultMatchers.status().isFound());

        mockMvc.perform(MockMvcRequestBuilders.get(PROTECTED_URL)
                .header("Authorization", "Bearer " + token))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.status()
                        .reason(ApiMessages.TOKEN_NOT_FOUND_ERROR.getMessage()));
    }

    @Test
    public void test_logout_only_revokes_current_session() throws Exception {
        val userDetails = createAndLoginUser();
        val firstToken = userDetails.get("token");
        val secondToken = tokenService.refreshToken(firstToken);
        val thirdToken = generateToken(userDetails.get("accountNumber"), userDetails.get("password"));
        tokenService.saveToken(thirdToken);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/users/logout")
                .header("Authorization", "Bearer " + secondToken))
                .andExpect(MockMvcResultMatchers.status().isFound());

        mockMvc.perform(MockMvcRequestBuilders.get(PROTECTED_URL)
                .header("Authorization", "Bearer " + thirdToken))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    public void test_refresh_token_endpoint_rotates_token() throws Exception {
        val oldToken = createAndLoginUser().get("token");

        val response = mockMvc.perform(MockMvcRequestBuilders.post("/api/users/refresh-token")
                .header("Authorization", "Bearer " + oldToken))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newToken = JsonPath.read(response, "$.token");

        Assertions.assertNotEquals(oldToken, newToken);

        mockMvc.perform(MockMvcRequestBuilders.get(PROTECTED_URL)
                .header("Authorization", "Bearer " + newToken))
                .andExpect(MockMvcResultMatchers.status().isOk());

        mockMvc.perform(MockMvcRequestBuilders.get(PROTECTED_URL)
                .header("Authorization", "Bearer " + oldToken))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    public void test_refresh_token_endpoint_with_revoked_token() throws Exception {
        val token = createAndLoginUser().get("token");
        tokenService.invalidateToken(token);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/users/refresh-token")
                .header("Authorization", "Bearer " + token))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    public void test_refresh_token_endpoint_without_authorization() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/users/refresh-token"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    public void test_password_reset_revokes_existing_tokens() throws Exception {
        val userDetails = createAndLoginUser();
        val token = userDetails.get("token");
        val user = userRepository.findByAccountAccountNumber(userDetails.get("accountNumber")).get();

        Assertions.assertTrue(userService.resetPassword(user, getRandomPassword()));

        Assertions.assertNull(tokenRepository.findByToken(token));
        mockMvc.perform(MockMvcRequestBuilders.get(PROTECTED_URL)
                .header("Authorization", "Bearer " + token))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

}
