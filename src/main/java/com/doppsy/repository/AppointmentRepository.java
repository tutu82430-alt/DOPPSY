package com.doppsy.repository;

import com.doppsy.entity.Appointment;
import com.doppsy.entity.Appointment.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Appointment} entities.
 *
 * <p>This is the most query-heavy repository in Doppsy — it drives
 * the zero-wait-time booking engine. All derived and JPQL queries are
 * designed to hit the composite index
 * {@code (provider_id, status, appointment_timestamp)}.</p>
 */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    // ----------------------------------------------------------------
    // Patient-facing queries
    // ----------------------------------------------------------------

    /**
     * Fetches all appointments for a specific patient, ordered by time descending.
     *
     * @param userId the patient's UUID
     * @return list of the patient's appointments
     */
    List<Appointment> findAllByUserUserIdOrderByAppointmentTimestampDesc(UUID userId);

    /**
     * Fetches appointments for a patient filtered by status.
     *
     * @param userId the patient's UUID
     * @param status the lifecycle status to filter by
     * @return list of matching appointments
     */
    List<Appointment> findAllByUserUserIdAndStatus(UUID userId, AppointmentStatus status);

    // ----------------------------------------------------------------
    // Provider-facing queries
    // ----------------------------------------------------------------

    /**
     * Returns all appointment slots owned by a provider, ordered by time.
     *
     * @param providerId the provider's UUID
     * @return list of all the provider's slots
     */
    List<Appointment> findAllByProviderProviderIdOrderByAppointmentTimestampAsc(UUID providerId);

    /**
     * Returns all slots in a given status for a provider (e.g., all BOOKED slots).
     *
     * @param providerId the provider's UUID
     * @param status     the status to filter by
     * @return list of matching slots
     */
    List<Appointment> findAllByProviderProviderIdAndStatus(UUID providerId, AppointmentStatus status);

    // ----------------------------------------------------------------
    // Zero-wait booking engine — core query
    // ----------------------------------------------------------------

    /**
     * Finds the next {@code AVAILABLE} slot for a provider at or after the
     * requested time. This query is the heart of the zero-wait booking engine
     * and is accelerated by the composite index on (provider_id, status, appointment_timestamp).
     *
     * @param providerId the target provider's UUID
     * @param from       the earliest acceptable appointment time (inclusive)
     * @return the very next available slot, if one exists
     */
    @Query("""
        SELECT a FROM Appointment a
        WHERE a.provider.providerId = :providerId
          AND a.status = 'AVAILABLE'
          AND a.appointmentTimestamp >= :from
        ORDER BY a.appointmentTimestamp ASC
        LIMIT 1
        """)
    Optional<Appointment> findNextAvailableSlot(
        @Param("providerId") UUID providerId,
        @Param("from") LocalDateTime from
    );

    /**
     * Returns all {@code AVAILABLE} slots for a provider within a given time window.
     * Used to populate a provider's availability calendar on the patient UI.
     *
     * @param providerId the provider's UUID
     * @param start      window start (inclusive)
     * @param end        window end (inclusive)
     * @return list of available slots within the window
     */
    List<Appointment> findAllByProviderProviderIdAndStatusAndAppointmentTimestampBetween(
        UUID providerId,
        AppointmentStatus status,
        LocalDateTime start,
        LocalDateTime end
    );

    // ----------------------------------------------------------------
    // Admin / analytics queries
    // ----------------------------------------------------------------

    /**
     * Counts all appointments by status — used for the admin dashboard KPIs.
     *
     * @param status the status to count
     * @return total count of slots in that status
     */
    long countByStatus(AppointmentStatus status);
}
