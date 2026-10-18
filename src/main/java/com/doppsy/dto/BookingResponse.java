package com.doppsy.dto;

import com.doppsy.entity.Appointment.AppointmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response payload returned after a successful booking or appointment lookup.
 *
 * <p>Combines appointment, provider, financial, and Razorpay payment data
 * into a single client-friendly object — no follow-up calls needed.</p>
 *
 * <p>When the status is {@code PENDING_PAYMENT}, the {@code razorpayOrderId}
 * and {@code razorpayKeyId} fields are populated so the client can open the
 * Razorpay checkout UI.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {

    /** UUID of the created or retrieved appointment. */
    private UUID appointmentId;

    /** UUID of the provider for this appointment. */
    private UUID providerId;

    /** Display name of the booked provider. */
    private String providerName;

    /** Name of the clinic / hospital. */
    private String clinicName;

    /** Scheduled UTC date-time of the appointment. */
    private LocalDateTime appointmentTimestamp;

    /** Current lifecycle status (e.g., PENDING_PAYMENT, BOOKED, CANCELLED). */
    private AppointmentStatus status;

    /**
     * Platform booking fee charged to the patient in INR.
     * Derived from the provider's subscription tier.
     */
    private BigDecimal bookingFee;

    /** UTC timestamp when this booking record was created. */
    private LocalDateTime createdAt;

    // ── Razorpay payment fields (populated during PENDING_PAYMENT) ────────

    /**
     * Razorpay order ID — present when status is {@code PENDING_PAYMENT}.
     * The client uses this to open the Razorpay checkout modal.
     */
    private String razorpayOrderId;

    /**
     * Razorpay publishable key ID — present when status is {@code PENDING_PAYMENT}.
     * Required by the Razorpay checkout script on the client side.
     */
    private String razorpayKeyId;
}
