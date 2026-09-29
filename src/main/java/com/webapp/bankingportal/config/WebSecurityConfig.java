package com.webapp.bankingportal.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
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

import com.webapp.bankingportal.entity.Role;
import com.webapp.bankingportal.security.JwtAccessDeniedHandler;
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
            "/actuator/health"
    };

    private static final String[] CUSTOMER_URLS = {
            "/api/account/**",
            "/api/dashboard/**",
            "/api/users/update"
    };

    private static final String[] ADMIN_URLS = {
            "/api/admin/**",
            "/actuator/**"
    };

    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
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
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(ADMIN_URLS).hasRole(Role.ADMIN.name())
                        .requestMatchers(CUSTOMER_URLS).hasRole(Role.CUSTOMER.name())
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> {
                    handling.authenticationEntryPoint(jwtAuthenticationEntryPoint);
                    handling.accessDeniedHandler(jwtAccessDeniedHandler);
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
