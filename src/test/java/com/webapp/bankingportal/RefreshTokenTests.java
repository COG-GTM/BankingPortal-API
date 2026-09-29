package com.webapp.bankingportal;

import java.util.Date;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.jayway.jsonpath.JsonPath;
import com.webapp.bankingportal.dto.LoginRequest;
import com.webapp.bankingportal.dto.RefreshTokenRequest;
import com.webapp.bankingportal.repository.RefreshTokenRepository;
import com.webapp.bankingportal.repository.TokenRepository;
import com.webapp.bankingportal.service.RefreshTokenServiceImpl;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;

import lombok.val;

public class RefreshTokenTests extends BaseTest {

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    TokenRepository tokenRepository;

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders
                .post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new RefreshTokenRequest(refreshToken))));
    }

    private String[] refreshOk(String refreshToken) throws Exception {
        val body = refresh(refreshToken)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.token").isNotEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return new String[] { JsonPath.read(body, "$.token"), JsonPath.read(body, "$.refreshToken") };
    }

    private ResultActions getDashboard(String accessToken) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders
                .get("/api/dashboard/user")
                .header("Authorization", "Bearer " + accessToken));
    }

    private String familyOf(String refreshToken) {
        return refreshTokenRepository.findByTokenHash(RefreshTokenServiceImpl.hash(refreshToken))
                .orElseThrow().getFamilyId();
    }

    @Test
    public void test_login_issues_refresh_token_stored_only_as_hash() throws Exception {
        val refreshToken = createAndLoginUser().get("refreshToken");

        Assertions.assertNotNull(refreshToken);
        Assertions.assertTrue(refreshTokenRepository.findByTokenHash(refreshToken).isEmpty());
        Assertions.assertTrue(refreshTokenRepository
                .findByTokenHash(RefreshTokenServiceImpl.hash(refreshToken)).isPresent());
    }

    @Test
    public void test_refresh_rotates_token_and_keeps_family() throws Exception {
        val userDetails = createAndLoginUser();
        val r1 = userDetails.get("refreshToken");

        val pair = refreshOk(r1);
        val a2 = pair[0];
        val r2 = pair[1];

        Assertions.assertNotEquals(r1, r2);
        Assertions.assertNotEquals(userDetails.get("token"), a2);
        Assertions.assertEquals(familyOf(r1), familyOf(r2));
        Assertions.assertNotNull(refreshTokenRepository
                .findByTokenHash(RefreshTokenServiceImpl.hash(r1)).orElseThrow().getUsedAt());

        getDashboard(a2).andExpect(MockMvcResultMatchers.status().isOk());

        // The new refresh token is itself rotatable.
        refreshOk(r2);
    }

    @Test
    public void test_reuse_of_rotated_refresh_token_revokes_entire_family() throws Exception {
        val userDetails = createAndLoginUser();
        val a1 = userDetails.get("token");
        val r1 = userDetails.get("refreshToken");
        val family = familyOf(r1);

        val pair2 = refreshOk(r1);
        val pair3 = refreshOk(pair2[1]);

        // r1 was already rotated: presenting it again is reuse.
        refresh(r1)
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.REFRESH_TOKEN_REUSE_DETECTED_ERROR.getMessage()));

        val familyTokens = refreshTokenRepository.findAllByFamilyId(family);
        Assertions.assertEquals(3, familyTokens.size());
        familyTokens.forEach(t -> Assertions.assertNotNull(t.getRevokedAt()));

        // The latest, never-used refresh token no longer works.
        refresh(pair3[1])
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.REFRESH_TOKEN_INVALID_ERROR.getMessage()));

        // Every access token issued in the family is revoked.
        for (val accessToken : new String[] { a1, pair2[0], pair3[0] }) {
            Assertions.assertNull(tokenRepository.findByToken(accessToken));
            getDashboard(accessToken).andExpect(MockMvcResultMatchers.status().isUnauthorized());
        }
    }

    @Test
    public void test_stolen_refresh_token_used_first_by_attacker_is_detected() throws Exception {
        val userDetails = createAndLoginUser();
        val stolen = userDetails.get("refreshToken");

        // Attacker redeems the stolen token first and gets a valid pair.
        val attackerPair = refreshOk(stolen);
        getDashboard(attackerPair[0]).andExpect(MockMvcResultMatchers.status().isOk());

        // Legitimate client later tries to refresh with the same token.
        refresh(stolen)
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.REFRESH_TOKEN_REUSE_DETECTED_ERROR.getMessage()));

        // Attacker is locked out: both their access and refresh tokens are dead.
        getDashboard(attackerPair[0]).andExpect(MockMvcResultMatchers.status().isUnauthorized());
        refresh(attackerPair[1]).andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    public void test_reuse_does_not_affect_other_families_of_same_account() throws Exception {
        val userDetails = createAndLoginUser();
        val r1 = userDetails.get("refreshToken");

        val secondLogin = mockMvc.perform(MockMvcRequestBuilders
                .post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.toJson(new LoginRequest(
                        userDetails.get("accountNumber"), userDetails.get("password")))))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        String otherAccess = JsonPath.read(secondLogin, "$.token");
        String otherRefresh = JsonPath.read(secondLogin, "$.refreshToken");

        refreshOk(r1);
        refresh(r1).andExpect(MockMvcResultMatchers.status().isUnauthorized());

        getDashboard(otherAccess).andExpect(MockMvcResultMatchers.status().isOk());
        refreshOk(otherRefresh);
    }

    @Test
    public void test_refresh_with_unknown_token() throws Exception {
        refresh("not-a-real-refresh-token")
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.REFRESH_TOKEN_INVALID_ERROR.getMessage()));
    }

    @Test
    public void test_refresh_with_empty_token() throws Exception {
        refresh("")
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.TOKEN_EMPTY_ERROR.getMessage()));
    }

    @Test
    public void test_refresh_with_expired_token() throws Exception {
        val refreshToken = createAndLoginUser().get("refreshToken");
        val stored = refreshTokenRepository
                .findByTokenHash(RefreshTokenServiceImpl.hash(refreshToken)).orElseThrow();
        stored.setExpiresAt(new Date(System.currentTimeMillis() - 1000));
        refreshTokenRepository.saveAndFlush(stored);

        refresh(refreshToken)
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.TOKEN_EXPIRED_ERROR.getMessage()));
    }

    @Test
    public void test_logout_revokes_refresh_token_family() throws Exception {
        val userDetails = createAndLoginUser();
        val refreshToken = userDetails.get("refreshToken");

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/users/logout")
                .header("Authorization", "Bearer " + userDetails.get("token")))
                .andExpect(MockMvcResultMatchers.status().is3xxRedirection());

        refresh(refreshToken)
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.content()
                        .string(ApiMessages.REFRESH_TOKEN_INVALID_ERROR.getMessage()));
    }

}
