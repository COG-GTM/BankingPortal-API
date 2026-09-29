package com.webapp.bankingportal.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.webapp.bankingportal.security.JwtAuthenticationEntryPoint;
import com.webapp.bankingportal.security.JwtAuthenticationFilter;
import com.webapp.bankingportal.service.TokenService;

import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

    private static final String[] PUBLIC_URLS = {
            "/api/users/register",
            "/api/users/login",
            "/api/auth/password-reset/verify-otp",
            "/api/auth/password-reset/send-otp",
            "/api/auth/password-reset",
            "/api/users/generate-otp",
            "/api/users/verify-otp",
            "swagger-ui.html",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/actuator/**"
    };

    /**
     * JSON-only API: nothing may be loaded, framed, or submitted from a response.
     */
    static final String API_CONTENT_SECURITY_POLICY = "default-src 'none'; frame-ancestors 'none'; "
            + "base-uri 'none'; form-action 'none'";

    /**
     * Swagger UI needs its own same-origin scripts, styles (some inline), images
     * and fetches to the OpenAPI document.
     */
    static final String DOCS_CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; "
            + "connect-src 'self'; object-src 'none'; frame-ancestors 'none'; "
            + "base-uri 'self'; form-action 'self'";

    static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;

    private static final RequestMatcher DOCS_REQUEST_MATCHER = new OrRequestMatcher(
            AntPathRequestMatcher.antMatcher("/swagger-ui.html"),
            AntPathRequestMatcher.antMatcher("/swagger-ui/**"),
            AntPathRequestMatcher.antMatcher("/v3/api-docs/**"));

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final TokenService tokenService;

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(tokenService).passwordEncoder(passwordEncoder());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // CSRF protection is disabled because the API is stateless and authenticates
        // solely via the Authorization: Bearer header, which browsers never attach
        // automatically to cross-site requests. See docs/security-headers-and-csrf.md.
        http.csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frameOptions -> frameOptions.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                new NegatedRequestMatcher(DOCS_REQUEST_MATCHER),
                                new ContentSecurityPolicyHeaderWriter(API_CONTENT_SECURITY_POLICY)))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                DOCS_REQUEST_MATCHER,
                                new ContentSecurityPolicyHeaderWriter(DOCS_CONTENT_SECURITY_POLICY))))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> {
                    handling.authenticationEntryPoint(jwtAuthenticationEntryPoint);
                })
                .sessionManagement(management -> {
                    management.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
                })

                .logout(logout -> logout
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(HttpServletResponse.SC_OK);
                        }));

        http.addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

}
