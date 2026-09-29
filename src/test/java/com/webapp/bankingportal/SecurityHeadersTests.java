package com.webapp.bankingportal;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.webapp.bankingportal.config.WebSecurityConfig;
import com.webapp.bankingportal.controller.AccountController;
import com.webapp.bankingportal.controller.DashboardController;
import com.webapp.bankingportal.security.JwtAuthenticationEntryPoint;
import com.webapp.bankingportal.service.AccountService;
import com.webapp.bankingportal.service.DashboardService;
import com.webapp.bankingportal.service.TokenService;
import com.webapp.bankingportal.service.TransactionService;

@WebMvcTest(controllers = { DashboardController.class, AccountController.class })
@Import({ WebSecurityConfig.class, JwtAuthenticationEntryPoint.class })
@TestPropertySource(properties = "spring.main.allow-circular-references=true")
public class SecurityHeadersTests {

    private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'; "
            + "base-uri 'none'; form-action 'none'";

    private static final String DOCS_CSP = "default-src 'self'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; "
            + "connect-src 'self'; object-src 'none'; frame-ancestors 'none'; "
            + "base-uri 'self'; form-action 'self'";

    private static final String HSTS = "max-age=31536000 ; includeSubDomains";

    private static final String VALID_TOKEN = "valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TokenService tokenService;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private AccountService accountService;

    @MockBean
    private TransactionService transactionService;

    private static final String DEPOSIT_BODY = "{\"pin\":\"1234\",\"amount\":100.0}";

    private void mockValidToken() throws Exception {
        when(tokenService.getUsernameFromToken(VALID_TOKEN)).thenReturn("123456");
        when(tokenService.loadUserByUsername(anyString()))
                .thenReturn(User.withUsername("123456").password("password").build());
    }

    private static ResultActions expectCommonHeaders(ResultActions result) throws Exception {
        return result
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    public void test_authenticated_api_response_has_security_headers() throws Exception {
        mockValidToken();

        expectCommonHeaders(mockMvc.perform(get("/api/dashboard/user")
                .secure(true)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", API_CSP))
                .andExpect(header().string("Strict-Transport-Security", HSTS));
    }

    @Test
    public void test_unauthenticated_api_response_has_security_headers() throws Exception {
        expectCommonHeaders(mockMvc.perform(get("/api/dashboard/user").secure(true)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Security-Policy", API_CSP))
                .andExpect(header().string("Strict-Transport-Security", HSTS));
    }

    @Test
    public void test_malformed_token_response_has_security_headers() throws Exception {
        expectCommonHeaders(mockMvc.perform(get("/api/dashboard/user")
                .secure(true)
                .header(HttpHeaders.AUTHORIZATION, "Basic abc")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Security-Policy", API_CSP))
                .andExpect(header().string("Strict-Transport-Security", HSTS));
    }

    @Test
    public void test_hsts_not_sent_over_plain_http() throws Exception {
        expectCommonHeaders(mockMvc.perform(get("/api/dashboard/user")))
                .andExpect(header().doesNotExist("Strict-Transport-Security"))
                .andExpect(header().string("Content-Security-Policy", API_CSP));
    }

    @Test
    public void test_api_docs_use_docs_content_security_policy() throws Exception {
        for (String path : new String[] { "/swagger-ui/index.html", "/swagger-ui.html", "/v3/api-docs" }) {
            expectCommonHeaders(mockMvc.perform(get(path).secure(true)))
                    .andExpect(header().string("Content-Security-Policy", DOCS_CSP))
                    .andExpect(header().string("Strict-Transport-Security", HSTS));
        }
    }

    @Test
    public void test_unauthenticated_post_without_csrf_token_is_rejected_as_unauthorized_not_forbidden()
            throws Exception {
        mockMvc.perform(post("/api/account/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(DEPOSIT_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void test_bearer_authenticated_post_without_csrf_token_succeeds() throws Exception {
        mockValidToken();

        mockMvc.perform(post("/api/account/deposit")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(DEPOSIT_BODY))
                .andExpect(status().isOk());

        verify(accountService).cashDeposit("123456", "1234", 100.0);
    }
}
