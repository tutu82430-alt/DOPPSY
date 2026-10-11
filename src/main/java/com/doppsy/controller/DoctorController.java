package com.doppsy.controller;

import com.doppsy.dto.BlockSlotRequest;
import com.doppsy.dto.BlockSlotResponse;
import com.doppsy.dto.DoctorResponse;
import com.doppsy.dto.RosterItemResponse;
import com.doppsy.dto.UpdateAppointmentStatusRequest;
import com.doppsy.entity.Provider;
import com.doppsy.exception.ResourceNotFoundException;
import com.doppsy.repository.ProviderRepository;
import com.doppsy.service.DoctorService;
import com.doppsy.util.JwtTokenExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for doctor-facing clinic operations:
 * viewing daily rosters, marking attendance/completion status, and blocking slots.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/doctor")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;
    private final ProviderRepository providerRepository;
    private final JwtTokenExtractor tokenExtractor;

    // -----------------------------------------------------------------------
    // GET /api/v1/doctor/profile
    // -----------------------------------------------------------------------

    /**
     * Returns the profile of the authenticated doctor (name, specialty, clinic, location).
     */
    @GetMapping("/profile")
    public ResponseEntity<DoctorResponse> getDoctorProfile(
        @RequestParam(value = "providerId", required = false) UUID providerIdParam,
        HttpServletRequest httpRequest
    ) {
        UUID providerId = (providerIdParam != null)
            ? providerIdParam
            : tokenExtractor.extractProviderIdOrThrow(httpRequest);

        Provider provider = providerRepository.findById(providerId)
            .orElseThrow(() -> new ResourceNotFoundException("Provider not found with ID: " + providerId));

        DoctorResponse response = DoctorResponse.builder()
            .providerId(provider.getProviderId())
            .fullName(provider.getFullName())
            .specialty(provider.getSpecialty())
            .clinicName(provider.getClinicName())
            .location(provider.getLocation())
            .subscriptionTier(provider.getMonthlySubscriptionTier())
            .isVerified(provider.getIsVerified())
            .build();

        return ResponseEntity.ok(response);
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/doctor/roster
    // -----------------------------------------------------------------------

    /**
     * Accepts a date parameter (defaulting to today's date) and returns
     * the authenticated doctor's full patient schedule with status, patient name,
     * age, contact, and appointment timestamp.
     *
     * <pre>
     * Request:
     *   GET /api/v1/doctor/roster?date=2026-10-11
     *   Authorization: Bearer &lt;token&gt;
     * </pre>
     *
     * @param date target date (optional, defaults to current date)
     * @param httpRequest HTTP request for provider resolution
     * @return 200 OK with list of {@link RosterItemResponse}
     */
    @GetMapping("/roster")
    public ResponseEntity<List<RosterItemResponse>> getRoster(
        @RequestParam(value = "date", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(value = "providerId", required = false) UUID providerIdParam,
        HttpServletRequest httpRequest
    ) {
        UUID providerId = (providerIdParam != null)
            ? providerIdParam
            : tokenExtractor.extractProviderIdOrThrow(httpRequest);

        log.info("[DOCTOR-API] GET /roster — providerId={}, date={}", providerId, date);
        List<RosterItemResponse> roster = doctorService.getRoster(providerId, date);
        return ResponseEntity.ok(roster);
    }

    // -----------------------------------------------------------------------
    // PATCH /api/v1/doctor/appointments/{id}/status
    // -----------------------------------------------------------------------

    /**
     * Accepts a status update (COMPLETED, CANCELLED, NO_SHOW) and updates
     * the appointment record in PostgreSQL. Enforces provider ownership.
     *
     * <pre>
     * Request:
     *   PATCH /api/v1/doctor/appointments/{id}/status
     *   Authorization: Bearer &lt;token&gt;
     *   Content-Type: application/json
     *   { "status": "COMPLETED" }
     * </pre>
     *
     * @param id appointment UUID
     * @param request target status
     * @param httpRequest HTTP request for provider resolution
     * @return 200 OK with updated {@link RosterItemResponse}
     */
    @PatchMapping("/appointments/{id}/status")
    public ResponseEntity<RosterItemResponse> updateAppointmentStatus(
        @PathVariable("id") UUID id,
        @Valid @RequestBody UpdateAppointmentStatusRequest request,
        @RequestParam(value = "providerId", required = false) UUID providerIdParam,
        HttpServletRequest httpRequest
    ) {
        UUID providerId = (providerIdParam != null)
            ? providerIdParam
            : tokenExtractor.extractProviderIdOrThrow(httpRequest);

        log.info("[DOCTOR-API] PATCH /appointments/{}/status -> {} by providerId={}",
            id, request.getStatus(), providerId);

        RosterItemResponse updated = doctorService.updateAppointmentStatus(
            id, providerId, request.getStatus());
        return ResponseEntity.ok(updated);
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/doctor/slots/block
    // -----------------------------------------------------------------------

    /**
     * Allows doctors or clinic receptionists to block out custom time intervals
     * (e.g., emergencies, surgery, personal leave) so patients cannot book them.
     *
     * <pre>
     * Request:
     *   POST /api/v1/doctor/slots/block
     *   Authorization: Bearer &lt;token&gt;
     *   Content-Type: application/json
     *   {
     *     "startTime": "2026-10-11T14:00:00",
     *     "endTime":   "2026-10-11T16:00:00",
     *     "reason":    "Emergency Surgery"
     *   }
     * </pre>
     *
     * @param request block parameters
     * @param httpRequest HTTP request for provider resolution
     * @return 200 OK with {@link BlockSlotResponse}
     */
    @PostMapping("/slots/block")
    public ResponseEntity<BlockSlotResponse> blockSlot(
        @Valid @RequestBody BlockSlotRequest request,
        @RequestParam(value = "providerId", required = false) UUID providerIdParam,
        HttpServletRequest httpRequest
    ) {
        UUID providerId = (providerIdParam != null)
            ? providerIdParam
            : tokenExtractor.extractProviderIdOrThrow(httpRequest);

        log.info("[DOCTOR-API] POST /slots/block — providerId={}, start={}, end={}, reason={}",
            providerId, request.getStartTime(), request.getEndTime(), request.getReason());

        BlockSlotResponse response = doctorService.blockSlots(providerId, request);
        return ResponseEntity.ok(response);
    }
}
