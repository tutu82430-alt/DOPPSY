package com.doppsy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response payload returned after successfully blocking out a time interval.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockSlotResponse {

    /** Provider ID whose schedule was blocked. */
    private UUID providerId;

    /** Number of slots marked or created as BLOCKED. */
    private int blockedSlotsCount;

    /** Start timestamp of blocked interval. */
    private LocalDateTime startTime;

    /** End timestamp of blocked interval. */
    private LocalDateTime endTime;

    /** Reason or memo for blocking the slot(s). */
    private String reason;

    /** Confirmation message. */
    private String message;
}
