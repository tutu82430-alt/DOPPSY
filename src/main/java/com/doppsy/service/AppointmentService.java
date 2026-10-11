package com.doppsy.service;

import com.doppsy.dto.BookingRequest;
import com.doppsy.dto.BookingResponse;
import com.doppsy.entity.Appointment;
import com.doppsy.entity.Appointment.AppointmentStatus;
import com.doppsy.entity.Provider;
import com.doppsy.entity.Provider.SubscriptionTier;
import com.doppsy.entity.User;
import com.doppsy.exception.ResourceNotFoundException;
import com.doppsy.exception.SlotUnavailableException;
import com.doppsy.repository.AppointmentRepository;
import com.doppsy.repository.ProviderRepository;
import com.doppsy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Booking engine service — the heart of Doppsy's zero-wait-time appointment system.
 *
 * <h3>Concurrency Guarantee</h3>
 * <p>{@link #bookAppointment} runs inside a single {@code @Transactional} boundary.
 * The call to {@code findSlotForBooking} acquires a
 * <strong>PESSIMISTIC_WRITE (SELECT … FOR UPDATE)</strong> row-level lock on the
 * target slot. Only one transaction at a time can hold this lock, so two patients
 * attempting to book the same slot simultaneously will serialize: the second will
 * see the updated {@code BOOKED} status and receive a 409 Conflict response.</p>
 *
 * <h3>Booking Fee Logic</h3>
 * <table border="1">
 *   <tr><th>Provider Tier</th><th>Platform Fee</th></tr>
 *   <tr><td>PREMIUM</td><td>₹ 50</td></tr>
 *   <tr><td>BASIC</td><td>₹ 100</td></tr>
 *   <tr><td>FREE</td><td>₹ 0</td></tr>
 * </table>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;

    // -----------------------------------------------------------------------
    // Book an appointment
    // -----------------------------------------------------------------------

    /**
     * Books a time slot for the authenticated patient with strict concurrency control.
     *
     * <p><strong>Flow:</strong></p>
     * <ol>
     *   <li>Verify the user (patient) exists.</li>
     *   <li>Verify the provider exists.</li>
     *   <li>Acquire a pessimistic write lock on the requested slot row.</li>
     *   <li>If the slot is already {@code BOOKED/COMPLETED/CANCELLED} → throw {@link SlotUnavailableException}.</li>
     *   <li>If the slot is {@code AVAILABLE} → claim it (set user + BOOKED + fee).</li>
     *   <li>If no slot row exists yet → create one directly as {@code BOOKED}.</li>
     * </ol>
     *
     * @param userId  UUID of the authenticated patient (from JWT)
     * @param request contains providerId and desired appointment timestamp
     * @return {@link BookingResponse} with full appointment details
     */
    @Transactional
    public BookingResponse bookAppointment(UUID userId, BookingRequest request) {

        // Step 1 — Verify patient ────────────────────────────────────────────
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));

        // Step 2 — Verify provider ───────────────────────────────────────────
        Provider provider = providerRepository.findById(request.getProviderId())
            .orElseThrow(() -> new ResourceNotFoundException("Provider", "providerId", request.getProviderId()));

        // Step 3 — Lock & inspect the slot (PESSIMISTIC_WRITE) ───────────────
        Optional<Appointment> existing = appointmentRepository.findSlotForBooking(
            request.getProviderId(),
            request.getAppointmentTimestamp()
        );

        Appointment appointment;

        if (existing.isPresent()) {
            Appointment slot = existing.get();

            // Step 4 — Reject if slot is not AVAILABLE ───────────────────────
            if (slot.getStatus() != AppointmentStatus.AVAILABLE) {
                throw new SlotUnavailableException(
                    String.format(
                        "The requested slot at %s with provider '%s' is already %s.",
                        request.getAppointmentTimestamp(),
                        provider.getFullName(),
                        slot.getStatus()
                    )
                );
            }

            // Step 5 — Claim the existing AVAILABLE slot ─────────────────────
            slot.setUser(user);
            slot.setStatus(AppointmentStatus.BOOKED);
            slot.setBookingFee(calculateFee(provider));
            appointment = appointmentRepository.save(slot);
            log.info("[BOOKING] Claimed existing slot — appointmentId={}, userId={}, providerId={}",
                appointment.getAppointmentId(), userId, provider.getProviderId());

        } else {
            // Step 6 — No pre-existing slot; create on-demand as BOOKED ──────
            appointment = new Appointment();
            appointment.setUser(user);
            appointment.setProvider(provider);
            appointment.setAppointmentTimestamp(request.getAppointmentTimestamp());
            appointment.setStatus(AppointmentStatus.BOOKED);
            appointment.setBookingFee(calculateFee(provider));
            appointment = appointmentRepository.save(appointment);
            log.info("[BOOKING] Created new slot on-demand — appointmentId={}, userId={}, providerId={}",
                appointment.getAppointmentId(), userId, provider.getProviderId());
        }

        return toBookingResponse(appointment, provider);
    }

    // -----------------------------------------------------------------------
    // Patient's appointment history
    // -----------------------------------------------------------------------

    /**
     * Returns all appointments for the authenticated patient, newest first.
     *
     * @param userId UUID of the authenticated patient
     * @return list of {@link BookingResponse}, may be empty
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> getMyAppointments(UUID userId) {
        // Verify user exists first
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", "userId", userId);
        }

        List<Appointment> appointments = appointmentRepository
            .findAllByUserUserIdOrderByAppointmentTimestampDesc(userId);

        log.debug("[BOOKING] getMyAppointments — userId={}, count={}", userId, appointments.size());

        return appointments.stream()
            .map(a -> toBookingResponse(a, a.getProvider()))
            .toList();
    }

    // -----------------------------------------------------------------------
    // Cancel an appointment
    // -----------------------------------------------------------------------

    /**
     * Cancels a {@code BOOKED} appointment owned by the requesting user.
     *
     * <p>Only the owning patient (or an ADMIN) may cancel. Appointments that
     * are already {@code COMPLETED} or {@code CANCELLED} cannot be cancelled again.</p>
     *
     * @param appointmentId UUID of the appointment to cancel
     * @param userId        UUID of the requesting patient (from JWT)
     * @return updated {@link BookingResponse} with status {@code CANCELLED}
     */
    @Transactional
    public BookingResponse cancelAppointment(UUID appointmentId, UUID userId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment", "appointmentId", appointmentId));

        // Ownership check — patient can only cancel their own appointments
        if (appointment.getUser() == null ||
            !appointment.getUser().getUserId().equals(userId)) {
            throw new SlotUnavailableException(
                "You are not authorised to cancel appointment: " + appointmentId);
        }

        // State machine guard — can only cancel BOOKED appointments
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new SlotUnavailableException(
                String.format("Cannot cancel an appointment with status '%s'. Only BOOKED appointments can be cancelled.",
                    appointment.getStatus())
            );
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment = appointmentRepository.save(appointment);

        log.info("[BOOKING] Cancelled appointment — appointmentId={}, userId={}", appointmentId, userId);
        return toBookingResponse(appointment, appointment.getProvider());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Calculates the platform booking fee based on the provider's subscription tier.
     *
     * <ul>
     *   <li>PREMIUM → ₹ 50  (reduced platform commission)</li>
     *   <li>BASIC   → ₹ 100 (standard platform fee)</li>
     *   <li>FREE    → ₹ 0   (no platform fee)</li>
     * </ul>
     */
    private BigDecimal calculateFee(Provider provider) {
        return switch (provider.getMonthlySubscriptionTier()) {
            case PREMIUM -> new BigDecimal("50.00");
            case BASIC   -> new BigDecimal("100.00");
            case FREE    -> BigDecimal.ZERO;
        };
    }

    /** Maps an {@link Appointment} entity + its {@link Provider} to a {@link BookingResponse} DTO. */
    private BookingResponse toBookingResponse(Appointment a, Provider provider) {
        return BookingResponse.builder()
            .appointmentId(a.getAppointmentId())
            .providerId(provider.getProviderId())
            .providerName(provider.getFullName())
            .clinicName(provider.getClinicName())
            .appointmentTimestamp(a.getAppointmentTimestamp())
            .status(a.getStatus())
            .bookingFee(a.getBookingFee())
            .createdAt(a.getCreatedAt())
            .build();
    }
}
