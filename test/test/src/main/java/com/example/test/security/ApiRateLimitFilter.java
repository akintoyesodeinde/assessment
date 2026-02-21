package com.example.test.security;

import com.example.test.dto.ErrorResponse;
import com.example.test.filter.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private final InMemoryRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public ApiRateLimitFilter(InMemoryRateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Value("${app.security.rate-limit.login.max-requests:5}")
    private int loginMaxRequests;

    @Value("${app.security.rate-limit.login.window-seconds:60}")
    private long loginWindowSeconds;

    @Value("${app.security.rate-limit.register.max-requests:5}")
    private int registerMaxRequests;

    @Value("${app.security.rate-limit.register.window-seconds:300}")
    private long registerWindowSeconds;

    @Value("${app.security.rate-limit.transfer.max-requests:20}")
    private int transferMaxRequests;

    @Value("${app.security.rate-limit.transfer.window-seconds:60}")
    private long transferWindowSeconds;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !"/api/auth/login".equals(path)
                && !"/api/auth/register".equals(path)
                && !"/api/wallet/transfers".equals(path)
                && !"/api/wallet/fund/me".equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        String keyPrefix;
        int maxRequests;
        Duration window;

        if ("/api/auth/login".equals(path)) {
            keyPrefix = "login";
            maxRequests = loginMaxRequests;
            window = Duration.ofSeconds(loginWindowSeconds);
        } else if ("/api/auth/register".equals(path)) {
            keyPrefix = "register";
            maxRequests = registerMaxRequests;
            window = Duration.ofSeconds(registerWindowSeconds);
        } else {
            keyPrefix = "transfer";
            maxRequests = transferMaxRequests;
            window = Duration.ofSeconds(transferWindowSeconds);
        }

        String principalKey = resolvePrincipalKey(request);
        InMemoryRateLimiter.RateLimitDecision decision =
                rateLimiter.tryAcquire(keyPrefix + ":" + principalKey, maxRequests, window);

        if (!decision.allowed()) {
            int status = HttpStatus.TOO_MANY_REQUESTS.value();
            response.setStatus(status);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));

            ErrorResponse errorResponse = ErrorResponse.builder()
                    .timestamp(Instant.now())
                    .status(status)
                    .error("Too Many Requests")
                    .message("Rate limit exceeded. Please retry later.")
                    .requestId(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER))
                    .build();

            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolvePrincipalKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
