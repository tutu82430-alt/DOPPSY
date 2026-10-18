package com.doppsy.repository;

import com.doppsy.entity.WaitlistEntry;
import com.doppsy.entity.WaitlistEntry.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link WaitlistEntry} entities.
 *
 * <p>Supports FIFO candidate selection, WhatsApp mobile phone resolution,
 * and concurrent claim settlement for the Doppsy Smart Waitlist Engine.</p>
 */
@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntry, UUID> {

    /**
     * Queries the top 3 patients who joined the waitlist for a specific doctor and date,
     * ordered strictly by arrival timestamp (FIFO). Eager-fetches User and Provider to
     * prevent N+1 queries during WhatsApp broadcast.
     *
     * @param providerId target healthcare provider UUID
     * @param targetDate target consultation calendar date
     * @param status     lifecycle status (typically WAITING)
     * @return top 3 waitlist records
     */
    @Query("""
        SELECT w FROM WaitlistEntry w
        JOIN FETCH w.user u
        JOIN FETCH w.provider p
        WHERE w.provider.providerId = :providerId
          AND w.targetDate = :targetDate
          AND w.status = :status
        ORDER BY w.createdAt ASC
        LIMIT 3
        """)
    List<WaitlistEntry> findTop3CandidatesForSlot(
        @Param("providerId") UUID providerId,
        @Param("targetDate") LocalDate targetDate,
        @Param("status") WaitlistStatus status
    );

    /**
     * Resolves an active, in-flight waitlist notification for a patient replying via WhatsApp.
     * Matches exact mobile number or country code suffix.
     *
     * @param mobile incoming sender mobile number
     * @param status expected status (e.g. NOTIFIED)
     * @return the most recent notified waitlist record if present
     */
    @Query("""
        SELECT w FROM WaitlistEntry w
        JOIN FETCH w.user u
        JOIN FETCH w.provider p
        LEFT JOIN FETCH w.notifiedSlot s
        WHERE (u.mobileNumber = :mobile OR :mobile LIKE CONCAT('%', u.mobileNumber) OR u.mobileNumber LIKE CONCAT('%', :mobile))
          AND w.status = :status
        ORDER BY w.notifiedAt DESC
        LIMIT 1
        """)
    Optional<WaitlistEntry> findActiveNotifiedEntryByMobile(
        @Param("mobile") String mobile,
        @Param("status") WaitlistStatus status
    );

    /**
     * Finds all waitlist candidates who were notified for a given slot and are still in NOTIFIED status.
     * Used by the claim engine to notify unselected candidates that the slot was taken.
     *
     * @param appointmentId the appointment slot ID
     * @param status        status filter (NOTIFIED)
     * @return list of candidates notified for this slot
     */
    @Query("""
        SELECT w FROM WaitlistEntry w
        JOIN FETCH w.user u
        JOIN FETCH w.provider p
        WHERE w.notifiedSlot.appointmentId = :appointmentId
          AND w.status = :status
        """)
    List<WaitlistEntry> findByNotifiedSlotIdAndStatus(
        @Param("appointmentId") UUID appointmentId,
        @Param("status") WaitlistStatus status
    );

    /**
     * Checks if a patient is already queued on the waitlist for a given doctor and date.
     */
    Optional<WaitlistEntry> findByUserUserIdAndProviderProviderIdAndTargetDateAndStatusIn(
        UUID userId,
        UUID providerId,
        LocalDate targetDate,
        List<WaitlistStatus> statuses
    );

    /**
     * Returns all waitlist entries for a patient, ordered by creation time descending.
     */
    List<WaitlistEntry> findByUserUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Returns all waitlist entries for a provider on a specific date.
     */
    List<WaitlistEntry> findByProviderProviderIdAndTargetDateOrderByCreatedAtAsc(
        UUID providerId,
        LocalDate targetDate
    );
}
