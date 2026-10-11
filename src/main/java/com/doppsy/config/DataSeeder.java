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

            List<Provider> savedProviders = providerRepository.saveAll(providers);
            log.info("[SEEDER] Successfully seeded {} providers.", savedProviders.size());

            seedAppointmentsForToday(savedProviders.get(0));
        };
    }

    private final com.doppsy.repository.UserRepository userRepository;
    private final com.doppsy.repository.AppointmentRepository appointmentRepository;

    private void seedAppointmentsForToday(Provider provider) {
        if (appointmentRepository.count() > 0) return;

        // Seed sample patients
        com.doppsy.entity.User p1 = new com.doppsy.entity.User();
        p1.setFullName("Aarav Sharma");
        p1.setMobileNumber("9876543210");
        p1.setAge(34);
        p1.setRole(com.doppsy.entity.User.UserRole.PATIENT);

        com.doppsy.entity.User p2 = new com.doppsy.entity.User();
        p2.setFullName("Ananya Patel");
        p2.setMobileNumber("9876543211");
        p2.setAge(29);
        p2.setRole(com.doppsy.entity.User.UserRole.PATIENT);

        com.doppsy.entity.User p3 = new com.doppsy.entity.User();
        p3.setFullName("Vikram Malhotra");
        p3.setMobileNumber("9876543212");
        p3.setAge(45);
        p3.setRole(com.doppsy.entity.User.UserRole.PATIENT);

        userRepository.saveAll(List.of(p1, p2, p3));

        java.time.LocalDate today = java.time.LocalDate.now();

        com.doppsy.entity.Appointment a1 = new com.doppsy.entity.Appointment();
        a1.setProvider(provider);
        a1.setUser(p1);
        a1.setAppointmentTimestamp(today.atTime(9, 30));
        a1.setStatus(com.doppsy.entity.Appointment.AppointmentStatus.BOOKED);
        a1.setBookingFee(new java.math.BigDecimal("50.00"));

        com.doppsy.entity.Appointment a2 = new com.doppsy.entity.Appointment();
        a2.setProvider(provider);
        a2.setUser(p2);
        a2.setAppointmentTimestamp(today.atTime(11, 0));
        a2.setStatus(com.doppsy.entity.Appointment.AppointmentStatus.BOOKED);
        a2.setBookingFee(new java.math.BigDecimal("50.00"));

        com.doppsy.entity.Appointment a3 = new com.doppsy.entity.Appointment();
        a3.setProvider(provider);
        a3.setUser(p3);
        a3.setAppointmentTimestamp(today.atTime(14, 30));
        a3.setStatus(com.doppsy.entity.Appointment.AppointmentStatus.BOOKED);
        a3.setBookingFee(new java.math.BigDecimal("50.00"));

        com.doppsy.entity.Appointment a4 = new com.doppsy.entity.Appointment();
        a4.setProvider(provider);
        a4.setUser(null);
        a4.setAppointmentTimestamp(today.atTime(16, 0));
        a4.setStatus(com.doppsy.entity.Appointment.AppointmentStatus.AVAILABLE);
        a4.setBookingFee(new java.math.BigDecimal("50.00"));

        appointmentRepository.saveAll(List.of(a1, a2, a3, a4));
        log.info("[SEEDER] Seeded 4 sample appointments for {} on {}", provider.getFullName(), today);
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
