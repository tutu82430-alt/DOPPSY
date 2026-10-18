package com.doppsy.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event dispatched to RabbitMQ when an appointment slot is cancelled.
 *
 * <p>Triggered when a patient cancels via WhatsApp conversational webhook (e.g. 'CANCEL')
 * or via the appointment management REST API. Consumed by the Smart Waitlist Engine
 * to alert the top FIFO candidates on the waitlist.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotCancelledEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The unique identifier of the released appointment slot.
     */
    private UUID appointmentId;

    /**
     * Healthcare provider / doctor owning this consultation slot.
     */
    private UUID providerId;

    /**
     * Doctor's full name for patient-facing alerts.
     */
    private String doctorName;

    /**
     * Clinic or hospital name where the consultation is hosted.
     */
    private String clinicName;

    /**
     * Scheduled date and time of the opened slot.
     */
    private LocalDateTime appointmentTimestamp;

    /**
     * Target date (UTC) of the consultation for waitlist matching.
     */
    private LocalDate slotDate;

    /**
     * User ID of the patient who released this slot (nullable).
     */
    private UUID cancelledByUserId;

    /**
     * UTC timestamp when the cancellation occurred.
     */
    private LocalDateTime cancelledAt;
}
