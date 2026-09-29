package com.webapp.bankingportal;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.webapp.bankingportal.config.WebSecurityConfig;
import com.webapp.bankingportal.security.JwtAuthenticationEntryPoint;
import com.webapp.bankingportal.service.TokenService;

/**
 * Exercises the real {@link WebSecurityConfig} filter chain against stub
 * endpoints, so no database, Redis or mail server is required.
 */
@WebMvcTest(controllers = SecurityHeadersTests.StubController.class,
        properties = "spring.main.allow-circular-references=true")
@Import({ WebSecurityConfig.class, JwtAuthenticationEntryPoint.class, SecurityHeadersTests.StubController.class })
class SecurityHeadersTests {

    private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TokenService tokenService;

    @RestController
    static class StubController {

        @PostMapping("/api/users/login")
        String login() {
            return "{}";
        }

        @GetMapping("/api/account/pin/check")
        String protectedEndpoint() {
            return "{}";
        }

        @GetMapping("/swagger-ui/index.html")
        String swaggerUi() {
            return "<html></html>";
        }
    }

    private static void assertCommonHeaders(ResultActions result) throws Exception {
        result.andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void publicApiResponse_hasStrictSecurityHeaders() throws Exception {
        var result = mockMvc.perform(post("/api/users/login").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", API_CSP))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
        assertCommonHeaders(result);
    }

    @Test
    void unauthenticatedErrorResponse_stillHasSecurityHeaders() throws Exception {
        var result = mockMvc.perform(get("/api/account/pin/check").secure(true))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Security-Policy", API_CSP))
                .andExpect(header().exists("Strict-Transport-Security"));
        assertCommonHeaders(result);
    }

    @Test
    void hsts_isOnlySentOverHttps() throws Exception {
        mockMvc.perform(post("/api/users/login"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Strict-Transport-Security"))
                .andExpect(header().string("Content-Security-Policy", API_CSP));
    }

    @Test
    void swaggerUi_getsRelaxedButFramingDeniedCsp() throws Exception {
        var result = mockMvc.perform(get("/swagger-ui/index.html").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().stringValues("Content-Security-Policy", Matchers.<String>iterableWithSize(1)));
        assertCommonHeaders(result);
    }

    @Test
    void stateChangingRequestWithoutCsrfToken_isNotRejected() throws Exception {
        mockMvc.perform(post("/api/users/login").secure(true))
                .andExpect(status().isOk());
    }
}
