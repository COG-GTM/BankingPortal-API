package com.webapp.bankingportal.ratelimit;

import java.io.IOException;
import java.time.Clock;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.webapp.bankingportal.dto.ErrorResponse;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.JsonUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Throttles unauthenticated authentication endpoints per client IP. Requests
 * over the configured limit are rejected with 429 before reaching Spring
 * Security or the controllers.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String HEADER_LIMIT = "X-RateLimit-Limit";
    public static final String HEADER_REMAINING = "X-RateLimit-Remaining";

    private final RateLimitProperties properties;
    private final FixedWindowRateLimiter rateLimiter;

    public RateLimitFilter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.rateLimiter = new FixedWindowRateLimiter(clock);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled()
                || !HttpMethod.POST.matches(request.getMethod())
                || RateLimitPolicy.forPath(resolvePath(request)).isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        final RateLimitPolicy policy = RateLimitPolicy.forPath(resolvePath(request)).orElseThrow();
        final RateLimitProperties.Policy settings = policy.settings(properties);
        final String key = policy.name() + ":" + resolveClientIp(request);

        final FixedWindowRateLimiter.Decision decision = rateLimiter.tryAcquire(
                key, settings.getMaxRequests(), settings.getWindow());

        response.setHeader(HEADER_LIMIT, String.valueOf(decision.limit()));
        response.setHeader(HEADER_REMAINING, String.valueOf(decision.remaining()));

        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JsonUtil.toJson(new ErrorResponse(
                String.format(ApiMessages.RATE_LIMIT_EXCEEDED.getMessage(), decision.retryAfterSeconds()))));
    }

    private static String resolvePath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (properties.isTrustForwardedHeaders()) {
            final String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
