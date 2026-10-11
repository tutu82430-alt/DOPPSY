package com.doppsy.dto;

import com.doppsy.entity.User.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response payload returned after successful OTP verification.
 *
 * <p>Contains the JWT bearer token and enough user context for the
 * mobile client to bootstrap its session without an extra profile call.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    /** JWT bearer token — include in every subsequent request as {@code Authorization: Bearer <token>}. */
    private String token;

    /** UUID of the authenticated user. */
    private UUID userId;

    /** Display name of the authenticated user. */
    private String fullName;

    /** Platform role — PATIENT or ADMIN. */
    private UserRole role;

    /** {@code true} when the user was auto-registered during this OTP verification. */
    private boolean newUser;
}
