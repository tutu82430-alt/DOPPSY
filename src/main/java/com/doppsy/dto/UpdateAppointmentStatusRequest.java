package com.doppsy.dto;

import com.doppsy.entity.Appointment.AppointmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for updating the status of an appointment on the doctor's roster.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAppointmentStatusRequest {

    /**
     * Target status: COMPLETED, CANCELLED, NO_SHOW.
     */
    @NotNull(message = "Status cannot be null")
    private AppointmentStatus status;
}
