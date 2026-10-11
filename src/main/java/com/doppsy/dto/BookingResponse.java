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
 * <p>Combines appointment, provider, and financial data into a single
 * client-friendly object — no follow-up calls needed.</p>
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

    /** Current lifecycle status (e.g., BOOKED, CANCELLED). */
    private AppointmentStatus status;

    /**
     * Platform booking fee charged to the patient in INR.
     * Derived from the provider's subscription tier.
     */
    private BigDecimal bookingFee;

    /** UTC timestamp when this booking record was created. */
    private LocalDateTime createdAt;
}
