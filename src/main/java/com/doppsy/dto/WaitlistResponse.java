package com.doppsy.dto;

import com.doppsy.entity.WaitlistEntry.WaitlistStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response payload describing a patient's waitlist registration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistResponse {
    private UUID waitlistId;
    private UUID userId;
    private String patientName;
    private String patientMobile;
    private UUID providerId;
    private String doctorName;
    private String clinicName;
    private LocalDate targetDate;
    private WaitlistStatus status;
    private LocalDateTime notifiedAt;
    private LocalDateTime createdAt;
}
