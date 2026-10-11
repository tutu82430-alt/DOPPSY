package com.doppsy.util;

import com.doppsy.exception.InvalidOtpException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Helper component that extracts and validates the JWT bearer token from an
 * incoming {@link HttpServletRequest} and resolves the authenticated user's UUID.
 *
 * <p>All secured endpoints inject this component to identify the caller without
 * repeating token-parsing boilerplate in every controller method.</p>
 *
 * <h3>Usage in a controller</h3>
 * <pre>
 *   UUID userId = tokenExtractor.extractUserIdOrThrow(request);
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class JwtTokenExtractor {

    private final JwtUtil jwtUtil;

    private final ProviderRepository providerRepository;

    /**
     * Extracts the JWT from the {@code Authorization: Bearer <token>} header,
     * validates the signature, and returns the authenticated user's UUID.
     *
     * @param request the incoming HTTP request
     * @return the authenticated user's {@link UUID}
     * @throws InvalidOtpException (HTTP 401) if the header is missing, malformed,
     *                             or the token is invalid/expired
     */
    public UUID extractUserIdOrThrow(HttpServletRequest request) {
        String token = resolveToken(request);
        try {
            Claims claims = jwtUtil.parseToken(token);
            String userIdStr = claims.get("userId", String.class);
            if (userIdStr == null) {
                throw new InvalidOtpException("Token does not contain a userId claim.");
            }
            return UUID.fromString(userIdStr);
        } catch (InvalidOtpException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidOtpException("Invalid or expired token: " + e.getMessage());
        }
    }

    /**
     * Resolves the provider ID for doctor endpoints:
     * 1. Checks 'X-Provider-Id' request header
     * 2. Checks 'providerId' query parameter
     * 3. Checks 'providerId' claim in the JWT
     * 4. Fallback in development: returns the first verified provider in the database
     *
     * @param request the incoming HTTP request
     * @return resolved provider UUID
     */
    public UUID extractProviderIdOrThrow(HttpServletRequest request) {
        // 1. Check X-Provider-Id header
        String headerPid = request.getHeader("X-Provider-Id");
        if (StringUtils.hasText(headerPid)) {
            try {
                return UUID.fromString(headerPid.trim());
            } catch (IllegalArgumentException ignored) {}
        }

        // 2. Check query parameter
        String paramPid = request.getParameter("providerId");
        if (StringUtils.hasText(paramPid)) {
            try {
                return UUID.fromString(paramPid.trim());
            } catch (IllegalArgumentException ignored) {}
        }

        // 3. Check JWT token claim if token present
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            try {
                String token = bearerToken.substring(7);
                Claims claims = jwtUtil.parseToken(token);
                String claimPid = claims.get("providerId", String.class);
                if (StringUtils.hasText(claimPid)) {
                    return UUID.fromString(claimPid);
                }
            } catch (Exception ignored) {}
        }

        // 4. Default fallback to first verified provider for easy demo/testing
        return providerRepository.findAllByIsVerified(true).stream()
            .findFirst()
            .map(com.doppsy.entity.Provider::getProviderId)
            .orElseGet(() -> providerRepository.findAll().stream()
                .findFirst()
                .map(com.doppsy.entity.Provider::getProviderId)
                .orElseThrow(() -> new InvalidOtpException("No provider found in database.")));
    }

    /**
     * Extracts the raw JWT string from the Authorization header.
     *
     * @throws InvalidOtpException if the header is absent or not in Bearer format
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(bearerToken) || !bearerToken.startsWith("Bearer ")) {
            throw new InvalidOtpException(
                "Missing or malformed Authorization header. Expected: 'Bearer <token>'");
        }
        return bearerToken.substring(7);
    }
}
