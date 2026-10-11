package com.doppsy.service;

import com.doppsy.dto.AuthResponse;
import com.doppsy.dto.SendOtpRequest;
import com.doppsy.dto.VerifyOtpRequest;
import com.doppsy.entity.User;
import com.doppsy.entity.User.UserRole;
import com.doppsy.exception.InvalidOtpException;
import com.doppsy.repository.UserRepository;
import com.doppsy.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles mobile-OTP authentication for the Doppsy platform.
 *
 * <h3>Mock OTP Flow (Development / Testing)</h3>
 * <ol>
 *   <li>Client calls {@code POST /api/v1/auth/send-otp} with a mobile number.</li>
 *   <li>In production this triggers an SMS gateway; here we log the OTP instead.</li>
 *   <li>Client calls {@code POST /api/v1/auth/verify-otp} with the same number and {@code 123456}.</li>
 *   <li>If the user does not exist they are auto-registered; either way a JWT is returned.</li>
 * </ol>
 *
 * <p>Replace the mock OTP check with a Redis-backed OTP store + SMS provider (e.g. Twilio / MSG91)
 * before going to production.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    /** Accepted test OTP — injected from {@code doppsy.mock-otp} in application.yml. */
    @Value("${doppsy.mock-otp}")
    private String mockOtp;

    // -----------------------------------------------------------------------
    // Send OTP
    // -----------------------------------------------------------------------

    /**
     * Simulates sending an OTP to the given mobile number.
     *
     * <p>In a real implementation this would call an SMS gateway and store a
     * time-limited OTP in Redis keyed by mobile number. Here we simply log it.</p>
     *
     * @param request the mobile number to send an OTP to
     */
    public void sendOtp(SendOtpRequest request) {
        String mobile = request.getMobileNumber();
        // ── TODO (Production): generate a random 6-digit OTP, cache it in Redis
        //    with a 5-minute TTL, then call your SMS provider. ──────────────────
        log.info("[MOCK OTP] Sending OTP '{}' to mobile: {}", mockOtp, mobile);
    }

    // -----------------------------------------------------------------------
    // Verify OTP
    // -----------------------------------------------------------------------

    /**
     * Validates the OTP and returns a JWT bearer token.
     *
     * <ul>
     *   <li>If the OTP does not match → throws {@link InvalidOtpException} (HTTP 401).</li>
     *   <li>If the user is new → auto-registers them as a PATIENT.</li>
     *   <li>If the user already exists → fetches their record.</li>
     * </ul>
     *
     * @param request mobile number + OTP entered by the user
     * @return {@link AuthResponse} containing the JWT, userId, fullName, role
     */
    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        // 1. Validate OTP ────────────────────────────────────────────────────
        if (!mockOtp.equals(request.getOtp())) {
            throw new InvalidOtpException("Invalid OTP. Please try again.");
        }

        // 2. Find or create user ─────────────────────────────────────────────
        boolean isNewUser = !userRepository.existsByMobileNumber(request.getMobileNumber());
        User user;

        if (isNewUser) {
            user = new User();
            user.setMobileNumber(request.getMobileNumber());
            user.setFullName("User_" + request.getMobileNumber()); // placeholder — updated on profile setup
            user.setRole(UserRole.PATIENT);
            user = userRepository.save(user);
            log.info("[AUTH] Auto-registered new patient — userId={}, mobile={}",
                user.getUserId(), user.getMobileNumber());
        } else {
            user = userRepository.findByMobileNumber(request.getMobileNumber())
                .orElseThrow(() -> new InvalidOtpException("User lookup failed after existence check"));
            log.info("[AUTH] Existing user verified — userId={}, role={}", user.getUserId(), user.getRole());
        }

        // 3. Generate JWT ─────────────────────────────────────────────────────
        String token = jwtUtil.generateToken(
            user.getUserId(),
            user.getMobileNumber(),
            user.getRole().name()
        );

        return AuthResponse.builder()
            .token(token)
            .userId(user.getUserId())
            .fullName(user.getFullName())
            .role(user.getRole())
            .newUser(isNewUser)
            .build();
    }
}
