package com.doppsy.controller;

import com.doppsy.dto.JoinWaitlistRequest;
import com.doppsy.dto.WaitlistResponse;
import com.doppsy.service.WaitlistService;
import com.doppsy.util.JwtTokenExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for the Doppsy Smart Waitlist Engine.
 *
 * <p>Allows patients to join the queue for a fully-booked doctor and date.
 * When a slot is released, the RabbitMQ Smart Waitlist Engine dispatches
 * notifications and manages concurrent claiming.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/waitlists")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;
    private final JwtTokenExtractor tokenExtractor;

    /**
     * Enqueues the authenticated patient onto the waitlist for a specific doctor and date.
     *
     * <pre>
     * POST /api/v1/waitlists/join
     * Headers: Authorization: Bearer &lt;jwt&gt;
     * Body: { "providerId": "...", "targetDate": "2026-10-25" }
     * </pre>
     */
    @PostMapping("/join")
    public ResponseEntity<WaitlistResponse> joinWaitlist(
        @Valid @RequestBody JoinWaitlistRequest joinRequest,
        HttpServletRequest httpRequest
    ) {
        UUID authenticatedPatientId = tokenExtractor.extractUserId(httpRequest);
        log.info("[WAITLIST REST] PatientID: {} requested to join waitlist for ProviderID: {} on {}",
            authenticatedPatientId, joinRequest.getProviderId(), joinRequest.getTargetDate());

        WaitlistResponse response = waitlistService.joinWaitlist(authenticatedPatientId, joinRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves all waitlist entries for the authenticated patient.
     *
     * <pre>
     * GET /api/v1/waitlists/me
     * Headers: Authorization: Bearer &lt;jwt&gt;
     * </pre>
     */
    @GetMapping("/me")
    public ResponseEntity<List<WaitlistResponse>> getMyWaitlistEntries(HttpServletRequest httpRequest) {
        UUID authenticatedPatientId = tokenExtractor.extractUserId(httpRequest);
        List<WaitlistResponse> responses = waitlistService.getMyWaitlistEntries(authenticatedPatientId);
        return ResponseEntity.ok(responses);
    }

    /**
     * Retrieves the waitlist queue for a specific doctor and date (e.g. for clinic staff or admin).
     *
     * <pre>
     * GET /api/v1/waitlists/provider/{providerId}?date=2026-10-25
     * </pre>
     */
    @GetMapping("/provider/{providerId}")
    public ResponseEntity<List<WaitlistResponse>> getDoctorWaitlist(
        @PathVariable UUID providerId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        List<WaitlistResponse> responses = waitlistService.getWaitlistForDoctor(providerId, date);
        return ResponseEntity.ok(responses);
    }
}
