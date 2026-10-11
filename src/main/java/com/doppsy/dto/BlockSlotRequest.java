package com.doppsy.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request payload to block out time intervals for a provider.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockSlotRequest {

    /** Start timestamp of the blocked window. */
    @NotNull(message = "startTime is required")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;

    /** End timestamp of the blocked window (defaults to startTime + 1 hour if null). */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endTime;

    /** Reason for the block (e.g., 'Emergency Surgery', 'Personal Leave', 'Clinic Maintenance'). */
    private String reason;
}
