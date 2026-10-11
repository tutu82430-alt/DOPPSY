package com.doppsy.config;

import com.doppsy.entity.Provider;
import com.doppsy.entity.Provider.Specialty;
import com.doppsy.entity.Provider.SubscriptionTier;
import com.doppsy.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Seeds the database with realistic sample provider data on first startup.
 *
 * <p>The seeder runs only when the {@code providers} table is empty —
 * it is safe to leave active in development; it is a no-op in production
 * once real providers have been onboarded.</p>
 *
 * <h3>Seed Data Summary</h3>
 * <ul>
 *   <li>2 × Allopathy (PREMIUM, BASIC)</li>
 *   <li>2 × Ayurveda  (PREMIUM, FREE)</li>
 *   <li>2 × Veterinary (BASIC, FREE)</li>
 * </ul>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataSeeder {

    private final ProviderRepository providerRepository;

    @Bean
    CommandLineRunner seedProviders() {
        return args -> {
            if (providerRepository.count() > 0) {
                log.info("[SEEDER] Providers table already has data — skipping seed.");
                return;
            }

            log.info("[SEEDER] Seeding 6 initial healthcare providers...");

            List<Provider> providers = List.of(

                // ── Allopathy 1 ─────────────────────────────────────────────
                buildProvider(
                    "Dr. Rohan Mehta",
                    Specialty.Allopathy,
                    "Mehta Multispeciality Clinic",
                    "Bandra West, Mumbai",
                    SubscriptionTier.PREMIUM,
                    true
                ),

                // ── Allopathy 2 ─────────────────────────────────────────────
                buildProvider(
                    "Dr. Priya Sharma",
                    Specialty.Allopathy,
                    "Sharma General & Internal Medicine",
                    "Koramangala, Bengaluru",
                    SubscriptionTier.BASIC,
                    true
                ),

                // ── Ayurveda 1 ──────────────────────────────────────────────
                buildProvider(
                    "Dr. Ananda Krishnan",
                    Specialty.Ayurveda,
                    "Vaidya Wellness Centre",
                    "Indiranagar, Bengaluru",
                    SubscriptionTier.PREMIUM,
                    true
                ),

                // ── Ayurveda 2 ──────────────────────────────────────────────
                buildProvider(
                    "Dr. Meena Joshi",
                    Specialty.Ayurveda,
                    "Joshi Panchkarma & Ayurvedic Clinic",
                    "Karol Bagh, New Delhi",
                    SubscriptionTier.FREE,
                    true
                ),

                // ── Veterinary 1 ────────────────────────────────────────────
                buildProvider(
                    "Dr. Sanjay Pillai",
                    Specialty.Veterinary,
                    "PawCare Animal Hospital",
                    "Powai, Mumbai",
                    SubscriptionTier.BASIC,
                    true
                ),

                // ── Veterinary 2 ────────────────────────────────────────────
                buildProvider(
                    "Dr. Lakshmi Iyer",
                    Specialty.Veterinary,
                    "Happy Tails Veterinary Clinic",
                    "Whitefield, Bengaluru",
                    SubscriptionTier.FREE,
                    false   // pending verification
                )
            );

            providerRepository.saveAll(providers);
            log.info("[SEEDER] Successfully seeded {} providers.", providers.size());
        };
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private Provider buildProvider(
        String fullName,
        Specialty specialty,
        String clinicName,
        String location,
        SubscriptionTier tier,
        boolean isVerified
    ) {
        Provider p = new Provider();
        p.setFullName(fullName);
        p.setSpecialty(specialty);
        p.setClinicName(clinicName);
        p.setLocation(location);
        p.setMonthlySubscriptionTier(tier);
        p.setIsVerified(isVerified);
        return p;
    }
}
