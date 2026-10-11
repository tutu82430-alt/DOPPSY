package com.doppsy.service;

import com.doppsy.dto.DoctorResponse;
import com.doppsy.entity.Provider;
import com.doppsy.entity.Provider.Specialty;
import com.doppsy.exception.ResourceNotFoundException;
import com.doppsy.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * Business logic for healthcare provider discovery and search.
 *
 * <p>Powers both the patient-facing doctor search screen and the
 * admin provider management panel.</p>
 *
 * <h3>Search Strategy</h3>
 * <ul>
 *   <li>Both {@code specialty} and {@code location} are optional — omitting both
 *       returns all verified providers.</li>
 *   <li>Location is matched case-insensitively as a substring (e.g., "Mumbai"
 *       matches "Andheri, Mumbai").</li>
 *   <li>Only verified providers are returned in patient-facing searches.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProviderService {

    private final ProviderRepository providerRepository;

    // -----------------------------------------------------------------------
    // Search / Discovery
    // -----------------------------------------------------------------------

    /**
     * Searches for verified providers filtered by specialty and/or location.
     *
     * @param specialtyStr optional specialty name string (e.g., "Allopathy")
     * @param location     optional location keyword (case-insensitive substring match)
     * @return list of matching {@link DoctorResponse} DTOs, never {@code null}
     */
    public List<DoctorResponse> searchProviders(String specialtyStr, String location) {
        boolean hasSpecialty = StringUtils.hasText(specialtyStr);
        boolean hasLocation  = StringUtils.hasText(location);

        List<Provider> providers;

        if (hasSpecialty && hasLocation) {
            // Both filters: specialty + location substring + verified
            Specialty specialty = parseSpecialty(specialtyStr);
            providers = providerRepository.findAllBySpecialtyAndIsVerified(specialty, true)
                .stream()
                .filter(p -> p.getLocation() != null &&
                             p.getLocation().toLowerCase().contains(location.toLowerCase()))
                .toList();

        } else if (hasSpecialty) {
            // Specialty only
            Specialty specialty = parseSpecialty(specialtyStr);
            providers = providerRepository.findAllBySpecialtyAndIsVerified(specialty, true);

        } else if (hasLocation) {
            // Location only
            providers = providerRepository.findByLocationContainingIgnoreCase(location)
                .stream()
                .filter(Provider::getIsVerified)
                .toList();

        } else {
            // No filters — return all verified providers
            providers = providerRepository.findAllByIsVerified(true);
        }

        log.debug("[PROVIDER SEARCH] specialty={}, location={} → {} results",
            specialtyStr, location, providers.size());

        return providers.stream()
            .map(this::toDto)
            .toList();
    }

    // -----------------------------------------------------------------------
    // Single provider fetch
    // -----------------------------------------------------------------------

    /**
     * Returns the full details of a single provider by UUID.
     *
     * @param providerId the provider's UUID
     * @return {@link DoctorResponse} for the requested provider
     * @throws ResourceNotFoundException if no provider exists with the given ID
     */
    public DoctorResponse getProviderById(UUID providerId) {
        Provider provider = providerRepository.findById(providerId)
            .orElseThrow(() -> new ResourceNotFoundException("Provider", "providerId", providerId));

        log.debug("[PROVIDER FETCH] providerId={}", providerId);
        return toDto(provider);
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Maps a {@link Provider} entity to a {@link DoctorResponse} DTO.
     */
    private DoctorResponse toDto(Provider p) {
        return DoctorResponse.builder()
            .providerId(p.getProviderId())
            .fullName(p.getFullName())
            .specialty(p.getSpecialty())
            .clinicName(p.getClinicName())
            .location(p.getLocation())
            .subscriptionTier(p.getMonthlySubscriptionTier())
            .isVerified(Boolean.TRUE.equals(p.getIsVerified()))
            .build();
    }

    /**
     * Safely parses a specialty string to the {@link Specialty} enum.
     *
     * @throws IllegalArgumentException if the value is not a valid specialty
     */
    private Specialty parseSpecialty(String specialtyStr) {
        try {
            // Enum values are title-case (e.g., "Allopathy") — try direct first
            return Specialty.valueOf(specialtyStr);
        } catch (IllegalArgumentException e) {
            // Also try capitalised form (e.g., "allopathy" → "Allopathy")
            String capitalised = specialtyStr.substring(0, 1).toUpperCase()
                + specialtyStr.substring(1).toLowerCase();
            try {
                return Specialty.valueOf(capitalised);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                    "Unknown specialty: '" + specialtyStr + "'. Valid values: " +
                    java.util.Arrays.toString(Specialty.values())
                );
            }
        }
    }
}
