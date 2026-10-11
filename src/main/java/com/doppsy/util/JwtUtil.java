package com.doppsy.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Utility component for generating and parsing Doppsy JWT bearer tokens.
 *
 * <p>Uses HMAC-SHA256 (HS256) signing. The secret is read from
 * {@code doppsy.jwt.secret} in {@code application.yml}.</p>
 *
 * <p><strong>Production note:</strong> rotate the secret via environment
 * variable ({@code DOPPSY_JWT_SECRET}) and store it in AWS Secrets Manager
 * or HashiCorp Vault — never commit the real secret to source control.</p>
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtUtil(
        @Value("${doppsy.jwt.secret}") String base64Secret,
        @Value("${doppsy.jwt.expiration-ms}") long expirationMs
    ) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Secret);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    /**
     * Generates a signed JWT token for the given user.
     *
     * @param userId   the authenticated user's UUID
     * @param mobile   the user's mobile number (stored as subject)
     * @param role     the user's role name string (e.g., "PATIENT")
     * @return signed JWT string
     */
    public String generateToken(UUID userId, String mobile, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
            .subject(mobile)
            .claims(Map.of(
                "userId", userId.toString(),
                "role", role
            ))
            .issuedAt(now)
            .expiration(expiry)
            .signWith(signingKey)
            .compact();
    }

    /**
     * Parses and validates a JWT token, returning its claims.
     *
     * @param token raw JWT string (without "Bearer " prefix)
     * @return parsed {@link Claims}
     * @throws io.jsonwebtoken.JwtException if the token is invalid or expired
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * Extracts the mobile number (subject) from a token without full validation.
     * Use {@link #parseToken(String)} for secure validation.
     */
    public String extractMobile(String token) {
        return parseToken(token).getSubject();
    }
}
