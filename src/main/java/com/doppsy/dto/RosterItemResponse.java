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
 * Data transfer object representing a single patient appointment on the doctor's roster.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RosterItemResponse {

    /** Unique identifier of the appointment. */
    private UUID appointmentId;

    /** Scheduled appointment timestamp. */
    private LocalDateTime appointmentTimestamp;

    /** Current lifecycle status (e.g. BOOKED, COMPLETED, NO_SHOW, BLOCKED). */
    private AppointmentStatus status;

    /** Patient's full name (or null / 'Unassigned' if open slot/blocked). */
    private String patientName;

    /** Patient's age (years). */
    private Integer patientAge;

    /** Patient's contact mobile number. */
    private String patientContact;

    /** Patient's user ID. */
    private UUID patientUserId;

    /** Fee charged for this slot. */
    private BigDecimal bookingFee;

    /** Timestamp when the slot/booking was created. */
    private LocalDateTime createdAt;
}
