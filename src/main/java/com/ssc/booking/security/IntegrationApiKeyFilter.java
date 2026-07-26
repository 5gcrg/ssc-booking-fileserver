package com.ssc.booking.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssc.booking.config.AppProperties;
import com.ssc.booking.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gates /api/v1/integration/files/** for external machine callers via X-API-Key — mirrors the
 * backend's integration filter. The matched registry Client (including its project folder) is
 * exposed as the "integration.client" request attribute for the controller. Rate limiting lives
 * here, after key resolution, keyed by client name — that sidesteps every filter-ordering
 * question the backend has to document. Server-to-server by design: no CORS entry exists for
 * this path. Keys are never logged.
 */
public class IntegrationApiKeyFilter extends OncePerRequestFilter {

    public static final String CLIENT_ATTRIBUTE = "integration.client";

    private static final Logger log = LoggerFactory.getLogger(IntegrationApiKeyFilter.class);
    private static final String HEADER = "X-API-Key";
    private static final String PROTECTED_PATH = "/api/v1/integration/files";

    private final IntegrationClientRegistry registry;
    private final AppProperties.Integration.RateLimit rateLimitConfig;
    private final ObjectMapper objectMapper;
    private final Map<String, TokenBucket> rateBuckets = new ConcurrentHashMap<>();

    public IntegrationApiKeyFilter(IntegrationClientRegistry registry,
                                   AppProperties appProperties,
                                   ObjectMapper objectMapper) {
        this.registry = registry;
        this.rateLimitConfig = appProperties.getIntegration().getRateLimit();
        this.objectMapper = objectMapper;
    }

    // Registered for every request in the chain — without this, the API-key check would block
    // the whole application (JWT file routes, health checks), not just the integration surface.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals(PROTECTED_PATH) || path.startsWith(PROTECTED_PATH + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        List<AppProperties.Integration.Client> clients = registry.clients();
        if (clients.isEmpty()) {
            log.warn("Project-files integration endpoint called but no clients are configured (app.integration.clients)");
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "NOT_CONFIGURED",
                "Integration API keys are not configured.");
            return;
        }

        String providedKey = request.getHeader(HEADER);
        if (providedKey == null || providedKey.isBlank()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "API_KEY_MISSING",
                "Missing X-API-Key header.");
            return;
        }

        // Compare against every client (no early exit) so response time doesn't reveal
        // which slot, if any, matched.
        AppProperties.Integration.Client matched = null;
        for (AppProperties.Integration.Client client : clients) {
            if (constantTimeEquals(client.getApiKey(), providedKey)) {
                matched = client;
            }
        }
        if (matched == null) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "API_KEY_INVALID", "Invalid API key.");
            return;
        }

        long retryAfterSeconds = tryConsumeToken(matched.getName());
        if (retryAfterSeconds >= 0) {
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            reject(response, 429, "RATE_LIMITED", "Rate limit exceeded. Please try again later.");
            logAccess(matched.getName(), request, 429);
            return;
        }

        request.setAttribute(CLIENT_ATTRIBUTE, matched);
        try {
            filterChain.doFilter(request, response);
        } finally {
            logAccess(matched.getName(), request, response.getStatus());
        }
    }

    /** Returns -1 when a token was consumed, otherwise the suggested Retry-After in seconds. */
    private long tryConsumeToken(String clientName) {
        if (!rateLimitConfig.isEnabled()) {
            return -1;
        }
        long now = System.currentTimeMillis();
        TokenBucket bucket = rateBuckets.computeIfAbsent(clientName,
            k -> new TokenBucket(rateLimitConfig.getCapacity(), now));
        synchronized (bucket) {
            double tokensPerSecond = rateLimitConfig.getRefillTokens()
                / (double) rateLimitConfig.getRefillPeriodSeconds();
            double elapsedSeconds = (now - bucket.lastRefillMillis) / 1000.0;
            bucket.tokens = Math.min(rateLimitConfig.getCapacity(),
                bucket.tokens + elapsedSeconds * tokensPerSecond);
            bucket.lastRefillMillis = now;
            if (bucket.tokens >= 1) {
                bucket.tokens -= 1;
                return -1;
            }
            return (long) Math.ceil((1 - bucket.tokens) / tokensPerSecond);
        }
    }

    private void logAccess(String clientName, HttpServletRequest request, int status) {
        log.info("project-files access client={} method={} path={} status={}",
            clientName, request.getMethod(), request.getRequestURI(), status);
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8));
    }

    private void reject(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
            new ErrorResponse(code, message, status)));
    }

    private static final class TokenBucket {
        double tokens;
        long lastRefillMillis;

        TokenBucket(double initialTokens, long now) {
            this.tokens = initialTokens;
            this.lastRefillMillis = now;
        }
    }
}
