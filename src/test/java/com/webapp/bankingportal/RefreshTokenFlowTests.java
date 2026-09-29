package com.webapp.bankingportal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.jayway.jsonpath.JsonPath;
import com.webapp.bankingportal.dto.LoginRequest;
import com.webapp.bankingportal.dto.RefreshTokenRequest;
import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.repository.UserRepository;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;

import lombok.val;

/**
 * End-to-end refresh token flow over HTTP against an in-memory H2 database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:refresh-flow;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.main.allow-circular-references=true",
        "jwt.expiration=3600000",
        "jwt.refresh-expiration=3600000",
        "geo.api.url=http://localhost:1/",
        "geo.api.key=test",
        "spring.mail.host=localhost"
})
public class RefreshTokenFlowTests {

    private static final String REFRESH_URL = "/api/auth/token/refresh";
    private static final String REVOKE_URL = "/api/auth/token/revoke";
    private static final String PROTECTED_URL = "/api/dashboard/user";
    private static final String JWT_SECRET = Encoders.BASE64
            .encode(Keys.secretKeyFor(SignatureAlgorithm.HS512).getEncoded());

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> JWT_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    public void test_login_returns_access_and_refresh_tokens() throws Exception {
        val tokens = registerAndLogin("flow1@example.com", "+14155550111");

        assertAccessTokenWorks(JsonPath.read(tokens, "$.token"));
        JsonPath.read(tokens, "$.refreshToken");
    }

    @Test
    public void test_refresh_rotates_tokens() throws Exception {
        val login = registerAndLogin("flow2@example.com", "+14155550112");
        String refreshToken = JsonPath.read(login, "$.refreshToken");

        val refreshed = refresh(refreshToken)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();

        String newAccessToken = JsonPath.read(refreshed, "$.token");
        String newRefreshToken = JsonPath.read(refreshed, "$.refreshToken");
        Assertions.assertNotEquals(refreshToken, newRefreshToken);
        assertAccessTokenWorks(newAccessToken);
        refresh(newRefreshToken).andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    public void test_refresh_token_reuse_revokes_family_and_access_tokens() throws Exception {
        val login = registerAndLogin("flow3@example.com", "+14155550113");
        String loginAccessToken = JsonPath.read(login, "$.token");
        String original = JsonPath.read(login, "$.refreshToken");

        val refreshed = refresh(original)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        String rotatedAccessToken = JsonPath.read(refreshed, "$.token");
        String rotatedRefreshToken = JsonPath.read(refreshed, "$.refreshToken");

        refresh(original)
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.REFRESH_TOKEN_REUSED_ERROR.getMessage()));

        refresh(rotatedRefreshToken)
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
        assertAccessTokenRejected(loginAccessToken);
        assertAccessTokenRejected(rotatedAccessToken);
    }

    @Test
    public void test_revoked_refresh_token_cannot_be_used() throws Exception {
        val login = registerAndLogin("flow4@example.com", "+14155550114");
        String refreshToken = JsonPath.read(login, "$.refreshToken");

        mockMvc.perform(MockMvcRequestBuilders.post(REVOKE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new RefreshTokenRequest(refreshToken))))
                .andExpect(MockMvcResultMatchers.status().isOk());

        refresh(refreshToken).andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    public void test_refresh_ignores_stale_access_token_header() throws Exception {
        val login = registerAndLogin("flow5@example.com", "+14155550115");
        String refreshToken = JsonPath.read(login, "$.refreshToken");

        mockMvc.perform(MockMvcRequestBuilders.post(REFRESH_URL)
                .header("Authorization", "Bearer not-a-valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new RefreshTokenRequest(refreshToken))))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    public void test_refresh_with_invalid_token() throws Exception {
        refresh("bogus")
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.REFRESH_TOKEN_INVALID_ERROR.getMessage()));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(REFRESH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new RefreshTokenRequest(refreshToken))));
    }

    private void assertAccessTokenWorks(String accessToken) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(PROTECTED_URL)
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    private void assertAccessTokenRejected(String accessToken) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(PROTECTED_URL)
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    private String registerAndLogin(String email, String phoneNumber) throws Exception {
        val password = "Str0ng!Passw0rd";
        val user = new User();
        user.setName("Flow User");
        user.setPassword(password);
        user.setEmail(email);
        user.setCountryCode("US");
        user.setPhoneNumber(phoneNumber);
        user.setAddress("1 Main St");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(user)))
                .andExpect(MockMvcResultMatchers.status().isOk());

        val accountNumber = userRepository.findByEmail(email).orElseThrow().getAccount().getAccountNumber();

        return mockMvc.perform(MockMvcRequestBuilders.post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new LoginRequest(accountNumber, password))))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

}
