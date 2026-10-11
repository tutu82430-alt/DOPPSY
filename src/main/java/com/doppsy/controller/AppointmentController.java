package com.doppsy.controller;

import com.doppsy.dto.BookingRequest;
import com.doppsy.dto.BookingResponse;
import com.doppsy.service.AppointmentService;
import com.doppsy.util.JwtTokenExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for the Doppsy zero-wait-time appointment booking system.
 *
 * <p>All endpoints are secured by JWT — the caller must include a valid
 * {@code Authorization: Bearer <token>} header obtained from
 * {@code POST /api/v1/auth/verify-otp}. The authenticated user's UUID is
 * extracted server-side from the token; it cannot be spoofed by the client.</p>
 *
 * <h3>Endpoints</h3>
 * <pre>
 *   POST  /api/v1/appointments/book         — Book a slot
 *   GET   /api/v1/appointments/me           — List my appointments
 *   PATCH /api/v1/appointments/{id}/cancel  — Cancel a booking
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final JwtTokenExtractor  tokenExtractor;

    // -----------------------------------------------------------------------
    // POST /api/v1/appointments/book
    // -----------------------------------------------------------------------

    /**
     * Books a time slot for the authenticated patient.
     *
     * <p>The user ID is extracted from the JWT bearer token — never from the request body.</p>
     *
     * <pre>
     * Request:
     *   POST /api/v1/appointments/book
     *   Authorization: Bearer &lt;token&gt;
     *   Content-Type: application/json
     *   {
     *     "providerId":            "uuid-of-provider",
     *     "appointmentTimestamp":  "2026-11-01T10:30:00"
     *   }
     *
     * Response 201 Created:
     *   {
     *     "appointmentId":        "uuid",
     *     "providerId":           "uuid",
     *     "providerName":         "Dr. Rohan Mehta",
     *     "clinicName":           "Mehta Multispeciality Clinic",
     *     "appointmentTimestamp": "2026-11-01T10:30:00",
     *     "status":               "BOOKED",
     *     "bookingFee":           50.00,
     *     "createdAt":            "2026-10-11T21:30:00"
     *   }
     * </pre>
     *
     * @param request     booking details (providerId + appointmentTimestamp)
     * @param httpRequest raw HTTP request for JWT extraction
     * @return 201 Created with {@link BookingResponse}
     */
    @PostMapping("/book")
    public ResponseEntity<BookingResponse> bookAppointment(
        @Valid @RequestBody BookingRequest request,
        HttpServletRequest httpRequest
    ) {
        UUID userId = tokenExtractor.extractUserIdOrThrow(httpRequest);
        log.info("[APPT] Book request — userId={}, providerId={}, time={}",
            userId, request.getProviderId(), request.getAppointmentTimestamp());

        BookingResponse response = appointmentService.bookAppointment(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/appointments/me
    // -----------------------------------------------------------------------

    /**
     * Returns all appointments for the authenticated patient, newest first.
     *
     * <pre>
     * Request:
     *   GET /api/v1/appointments/me
     *   Authorization: Bearer &lt;token&gt;
     *
     * Response 200 OK: [ { ...BookingResponse... }, ... ]
     * </pre>
     *
     * @param httpRequest raw HTTP request for JWT extraction
     * @return 200 OK with a list of {@link BookingResponse} (may be empty)
     */
    @GetMapping("/me")
    public ResponseEntity<List<BookingResponse>> getMyAppointments(
        HttpServletRequest httpRequest
    ) {
        UUID userId = tokenExtractor.extractUserIdOrThrow(httpRequest);
        log.info("[APPT] getMyAppointments — userId={}", userId);

        List<BookingResponse> appointments = appointmentService.getMyAppointments(userId);
        return ResponseEntity.ok(appointments);
    }

    // -----------------------------------------------------------------------
    // PATCH /api/v1/appointments/{id}/cancel
    // -----------------------------------------------------------------------

    /**
     * Cancels a {@code BOOKED} appointment owned by the authenticated user.
     *
     * <p>Returns 409 Conflict if:</p>
     * <ul>
     *   <li>The appointment does not belong to the requesting user.</li>
     *   <li>The appointment is already {@code CANCELLED} or {@code COMPLETED}.</li>
     * </ul>
     *
     * <pre>
     * Request:
     *   PATCH /api/v1/appointments/{id}/cancel
     *   Authorization: Bearer &lt;token&gt;
     *
     * Response 200 OK:
     *   { ...BookingResponse with status: "CANCELLED"... }
     * </pre>
     *
     * @param id          UUID of the appointment to cancel
     * @param httpRequest raw HTTP request for JWT extraction
     * @return 200 OK with updated {@link BookingResponse} in CANCELLED state
     */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelAppointment(
        @PathVariable UUID id,
        HttpServletRequest httpRequest
    ) {
        UUID userId = tokenExtractor.extractUserIdOrThrow(httpRequest);
        log.info("[APPT] Cancel request — appointmentId={}, userId={}", id, userId);

        BookingResponse response = appointmentService.cancelAppointment(id, userId);
        return ResponseEntity.ok(response);
    }
}
