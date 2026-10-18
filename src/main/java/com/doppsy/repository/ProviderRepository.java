package com.doppsy.repository;

import com.doppsy.entity.Provider;
import com.doppsy.entity.Provider.Specialty;
import com.doppsy.entity.Provider.SubscriptionTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Provider} entities.
 *
 * <p>Extends {@link JpaRepository} to inherit full CRUD operations,
 * pagination, and sorting out of the box. Custom derived-query methods
 * cover the most common provider discovery patterns.</p>
 */
@Repository
public interface ProviderRepository extends JpaRepository<Provider, UUID> {

    /**
     * Finds all providers for a given clinical specialty
     * (e.g., all Veterinary providers).
     *
     * @param specialty the {@link Specialty} to filter by
     * @return list of matching providers
     */
    List<Provider> findAllBySpecialty(Specialty specialty);

    /**
     * Finds all verified providers — used for the patient-facing directory.
     *
     * @param isVerified pass {@code true} to get only verified providers
     * @return list of matching providers
     */
    List<Provider> findAllByIsVerified(Boolean isVerified);

    /**
     * Finds all providers in a given subscription tier
     * (e.g., all PREMIUM providers).
     *
     * @param tier the {@link SubscriptionTier} to filter by
     * @return list of matching providers
     */
    List<Provider> findAllByMonthlySubscriptionTier(SubscriptionTier tier);

    /**
     * Finds verified providers of a specific specialty — the primary
     * query for the patient booking search screen.
     *
     * @param specialty  the clinical specialty
     * @param isVerified {@code true} for verified providers only
     * @return list of matching providers
     */
    List<Provider> findAllBySpecialtyAndIsVerified(Specialty specialty, Boolean isVerified);

    /**
     * Location-based partial search (city / area keyword).
     *
     * @param location partial location string
     * @return list of providers whose location contains the keyword
     */
    List<Provider> findByLocationContainingIgnoreCase(String location);

    /**
     * Checks whether a provider full name already exists
     * (used during onboarding deduplication).
     *
     * @param fullName the provider's name to check
     * @return {@code true} if a provider with this exact name exists
     */
    boolean existsByFullName(String fullName);

    /**
     * Resolves a provider by their registered WhatsApp mobile number.
     * Matches exact mobile or suffix (to handle international country codes like +91).
     *
     * @param mobile the doctor's mobile number
     * @return matching provider if registered
     */
    @org.springframework.data.jpa.repository.Query("""
        SELECT p FROM Provider p
        WHERE p.mobileNumber IS NOT NULL
          AND (p.mobileNumber = :mobile
               OR :mobile LIKE CONCAT('%', p.mobileNumber)
               OR p.mobileNumber LIKE CONCAT('%', :mobile))
        """)
    java.util.Optional<Provider> findByMatchingMobile(@org.springframework.data.repository.query.Param("mobile") String mobile);
}
