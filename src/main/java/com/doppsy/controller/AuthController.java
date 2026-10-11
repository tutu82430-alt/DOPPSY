package com.doppsy.controller;

import com.doppsy.dto.AuthResponse;
import com.doppsy.dto.SendOtpRequest;
import com.doppsy.dto.VerifyOtpRequest;
import com.doppsy.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for the Doppsy mobile-OTP authentication flow.
 *
 * <h3>Endpoints</h3>
 * <pre>
 *   POST /api/v1/auth/send-otp    — Request an OTP for a mobile number
 *   POST /api/v1/auth/verify-otp  — Verify the OTP and receive a JWT
 * </pre>
 *
 * <h3>Mock OTP</h3>
 * <p>During development use OTP {@code 123456} for any mobile number.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // -----------------------------------------------------------------------
    // POST /api/v1/auth/send-otp
    // -----------------------------------------------------------------------

    /**
     * Initiates the OTP flow for the given mobile number.
     *
     * <p>In production this triggers an SMS; in the mock environment it logs
     * the OTP to the console.</p>
     *
     * <pre>
     * Request:  POST /api/v1/auth/send-otp
     *           Content-Type: application/json
     *           { "mobileNumber": "9876543210" }
     *
     * Response: 200 OK
     *           { "message": "OTP sent successfully to 9876543210" }
     * </pre>
     *
     * @param request contains the mobile number
     * @return 200 OK with a confirmation message
     */
    @PostMapping("/send-otp")
    public ResponseEntity<Map<String, String>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        log.info("[AUTH] Send-OTP request for mobile: {}", request.getMobileNumber());
        authService.sendOtp(request);
        return ResponseEntity.ok(Map.of(
            "message", "OTP sent successfully to " + request.getMobileNumber(),
            "hint",    "Use OTP '123456' in development"
        ));
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/auth/verify-otp
    // -----------------------------------------------------------------------

    /**
     * Verifies the OTP and returns a JWT bearer token.
     *
     * <p>Auto-registers the user as a PATIENT if they are new.</p>
     *
     * <pre>
     * Request:  POST /api/v1/auth/verify-otp
     *           Content-Type: application/json
     *           { "mobileNumber": "9876543210", "otp": "123456" }
     *
     * Response: 200 OK
     *           {
     *             "token": "eyJhbGc...",
     *             "userId": "uuid",
     *             "fullName": "User_9876543210",
     *             "role": "PATIENT",
     *             "newUser": true
     *           }
     * </pre>
     *
     * @param request contains mobile number and OTP
     * @return 200 OK with {@link AuthResponse}
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        log.info("[AUTH] Verify-OTP request for mobile: {}", request.getMobileNumber());
        AuthResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(response);
    }
}
