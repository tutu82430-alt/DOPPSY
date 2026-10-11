package com.doppsy.dto;

import com.doppsy.entity.Provider.Specialty;
import com.doppsy.entity.Provider.SubscriptionTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Read-only response DTO for a healthcare provider.
 *
 * <p>Decouples the REST API surface from the JPA entity — internal fields
 * (e.g., created_at, raw JPA relations) are never exposed to clients.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorResponse {

    /** UUID of the provider. */
    private UUID providerId;

    /** Legal full name of the provider. */
    private String fullName;

    /** Clinical specialty (e.g., Allopathy, Veterinary). */
    private Specialty specialty;

    /** Name of the clinic or hospital. */
    private String clinicName;

    /** Human-readable address or area. */
    private String location;

    /** Current subscription tier on the Doppsy platform. */
    private SubscriptionTier subscriptionTier;

    /** Whether the provider has been verified by the Doppsy team. */
    private boolean isVerified;
}
