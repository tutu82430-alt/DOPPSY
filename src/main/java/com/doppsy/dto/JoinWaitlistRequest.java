package com.doppsy.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload for a patient joining the smart waitlist for a specific doctor and date.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinWaitlistRequest {

    @NotNull(message = "ProviderID is required to join the waitlist")
    private UUID providerId;

    @NotNull(message = "Target consultation date is required")
    private LocalDate targetDate;
}
