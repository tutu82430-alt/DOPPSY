package com.doppsy.repository;

import com.doppsy.entity.Appointment;
import com.doppsy.entity.Appointment.AppointmentStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
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

    /**
     * Fetches the full patient schedule/roster for a provider on a specific date range,
     * eager-fetching the patient (user) details to prevent N+1 queries.
     * Ordered chronologically by appointment timestamp.
     *
     * @param providerId the provider's UUID
     * @param startOfDay start of the target day (00:00:00)
     * @param endOfDay   end of the target day (23:59:59.999999)
     * @return chronological list of appointment slots
     */
    @Query("""
        SELECT a FROM Appointment a
        LEFT JOIN FETCH a.user u
        WHERE a.provider.providerId = :providerId
          AND a.appointmentTimestamp >= :startOfDay
          AND a.appointmentTimestamp <= :endOfDay
        ORDER BY a.appointmentTimestamp ASC
        """)
    List<Appointment> findRosterByProviderAndDate(
        @Param("providerId") UUID providerId,
        @Param("startOfDay") LocalDateTime startOfDay,
        @Param("endOfDay") LocalDateTime endOfDay
    );

    /**
     * Finds all appointment slots for a provider in a given interval (e.g. for blocking).
     *
     * @param providerId the provider's UUID
     * @param startTime  interval start
     * @param endTime    interval end
     * @return list of matching slots
     */
    @Query("""
        SELECT a FROM Appointment a
        WHERE a.provider.providerId = :providerId
          AND a.appointmentTimestamp >= :startTime
          AND a.appointmentTimestamp <= :endTime
        ORDER BY a.appointmentTimestamp ASC
        """)
    List<Appointment> findSlotsInInterval(
        @Param("providerId") UUID providerId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    // ----------------------------------------------------------------
    // Zero-wait booking engine — concurrency-safe slot claim
    // ----------------------------------------------------------------

    /**
     * Fetches the appointment at an exact provider + timestamp combination
     * and acquires a <strong>PESSIMISTIC_WRITE</strong> (SELECT … FOR UPDATE)
     * row-level lock on it.
     *
     * <p>This is the critical path for the booking transaction. Holding this
     * lock until the enclosing {@code @Transactional} method commits prevents
     * two concurrent requests from double-booking the same slot.</p>
     *
     * <p>A 5-second lock-acquisition timeout is set via the query hint;
     * callers should handle {@link jakarta.persistence.LockTimeoutException}.</p>
     *
     * @param providerId the provider's UUID
     * @param timestamp  the exact requested appointment time
     * @return the matching slot wrapped in an Optional (empty if slot doesn't exist yet)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("""
        SELECT a FROM Appointment a
        WHERE a.provider.providerId = :providerId
          AND a.appointmentTimestamp = :timestamp
        """)
    Optional<Appointment> findSlotForBooking(
        @Param("providerId") UUID providerId,
        @Param("timestamp") LocalDateTime timestamp
    );

    /**
     * Acquires a PESSIMISTIC_WRITE row-level lock by provider and timestamp.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("""
        SELECT a FROM Appointment a
        WHERE a.provider.providerId = :providerId
          AND a.appointmentTimestamp = :timestamp
        """)
    Optional<Appointment> findByProviderAndTimestampForUpdate(
        @Param("providerId") UUID providerId,
        @Param("timestamp") LocalDateTime timestamp
    );

    /**
     * Locks an appointment by its ID using a PESSIMISTIC_WRITE row-level lock.
     * Critical for the Smart Waitlist Engine to ensure the first responder to 'CLAIM'
     * acquires the slot atomically, preventing double-allocation race conditions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("""
        SELECT a FROM Appointment a
        JOIN FETCH a.provider p
        LEFT JOIN FETCH a.user u
        WHERE a.appointmentId = :appointmentId
        """)
    Optional<Appointment> findByIdForUpdate(@Param("appointmentId") UUID appointmentId);


    /**
     * Finds the next {@code AVAILABLE} slot for a provider at or after the
     * requested time. Accelerated by the composite index
     * {@code (provider_id, status, appointment_timestamp)}.
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

    // ----------------------------------------------------------------
    // WhatsApp Concierge & Reminder Queries
    // ----------------------------------------------------------------

    /**
     * Finds the patient's most recent BOOKED appointment by mobile number.
     * Matches exact mobile or suffix (to handle international country codes like +91).
     *
     * @param mobile the patient's incoming WhatsApp mobile number
     * @return the most recent booked appointment if one exists
     */
    @Query("""
        SELECT a FROM Appointment a
        JOIN FETCH a.provider p
        JOIN FETCH a.user u
        WHERE (u.mobileNumber = :mobile OR :mobile LIKE CONCAT('%', u.mobileNumber) OR u.mobileNumber LIKE CONCAT('%', :mobile))
          AND a.status = 'BOOKED'
        ORDER BY a.appointmentTimestamp DESC
        LIMIT 1
        """)
    Optional<Appointment> findMostRecentBookedByMobile(@Param("mobile") String mobile);

    /**
     * Finds all BOOKED appointments scheduled within a specific future window
     * (e.g. 24 hours from the current time) to dispatch automated reminder templates.
     *
     * @param windowStart start of reminder window (inclusive)
     * @param windowEnd   end of reminder window (exclusive)
     * @return list of matching appointments with eager provider and user associations
     */
    @Query("""
        SELECT a FROM Appointment a
        JOIN FETCH a.provider p
        JOIN FETCH a.user u
        WHERE a.status = 'BOOKED'
          AND a.appointmentTimestamp >= :windowStart
          AND a.appointmentTimestamp < :windowEnd
        ORDER BY a.appointmentTimestamp ASC
        """)
    List<Appointment> findBookedAppointmentsInWindow(
        @Param("windowStart") LocalDateTime windowStart,
        @Param("windowEnd") LocalDateTime windowEnd
    );

    // ----------------------------------------------------------------
    // Razorpay Payment Verification
    // ----------------------------------------------------------------

    /**
     * Finds an appointment by its Razorpay order ID.
     * Used during the payment verification callback flow.
     *
     * @param razorpayOrderId the Razorpay order ID
     * @return the matching appointment if found
     */
    Optional<Appointment> findByRazorpayOrderId(String razorpayOrderId);

    // ----------------------------------------------------------------
    // AI Voice Prescription Queries
    // ----------------------------------------------------------------

    /**
     * Finds the doctor's most recently COMPLETED appointment to attach
     * and route the generated AI prescription.
     *
     * @param providerId the doctor's UUID
     * @return the most recent completed appointment
     */
    @Query("""
        SELECT a FROM Appointment a
        JOIN FETCH a.provider p
        JOIN FETCH a.user u
        WHERE a.provider.providerId = :providerId
          AND a.status = 'COMPLETED'
        ORDER BY a.appointmentTimestamp DESC
        LIMIT 1
        """)
    Optional<Appointment> findMostRecentCompletedByProviderId(@Param("providerId") UUID providerId);

    /**
     * Target resolution for in-flight prescriptions: finds the most recent COMPLETED or BOOKED
     * appointment for the doctor.
     *
     * @param providerId the doctor's UUID
     * @return matching appointment with eager associations
     */
    @Query("""
        SELECT a FROM Appointment a
        JOIN FETCH a.provider p
        JOIN FETCH a.user u
        WHERE a.provider.providerId = :providerId
          AND a.status IN ('COMPLETED', 'BOOKED')
        ORDER BY CASE WHEN a.status = 'COMPLETED' THEN 1 ELSE 2 END, a.appointmentTimestamp DESC
        LIMIT 1
        """)
    Optional<Appointment> findMostRecentPrescriptionTargetAppointment(@Param("providerId") UUID providerId);
}
