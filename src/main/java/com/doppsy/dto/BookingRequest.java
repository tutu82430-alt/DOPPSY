package com.doppsy.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request body for booking an appointment slot.
 *
 * <p>The {@code userId} is NOT included here — it is extracted from the JWT
 * bearer token server-side to prevent spoofing.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequest {

    /**
     * UUID of the healthcare provider whose slot is being booked.
     */
    @NotNull(message = "Provider ID is required")
    private UUID providerId;

    /**
     * The exact UTC date-time of the desired appointment.
     * Must be a future timestamp.
     */
    @NotNull(message = "Appointment timestamp is required")
    @Future(message = "Appointment must be scheduled in the future")
    private LocalDateTime appointmentTimestamp;
}
