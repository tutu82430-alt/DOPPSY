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
