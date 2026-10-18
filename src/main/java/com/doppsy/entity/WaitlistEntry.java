package com.doppsy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a patient on the Smart Waitlist queue for a specific doctor and date.
 *
 * <p>When an existing patient cancels an appointment, RabbitMQ triggers the waitlist engine
 * to notify the top 3 FIFO patients on this waitlist. The first patient to reply 'CLAIM'
 * is atomically assigned the slot via a pessimistic database write lock.</p>
 */
@Entity
@Table(
    name = "waitlists",
    indexes = {
        @Index(name = "idx_waitlist_provider_date_status", columnList = "provider_id, target_date, status, created_at"),
        @Index(name = "idx_waitlist_user_status",          columnList = "user_id, status"),
        @Index(name = "idx_waitlist_notified_slot",        columnList = "notified_slot_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistEntry {

    public enum WaitlistStatus {
        WAITING,
        NOTIFIED,
        CLAIMED,
        EXPIRED,
        CANCELLED
    }

    @Id
    @UuidGenerator
    @Column(name = "waitlist_id", updatable = false, nullable = false)
    private UUID waitlistId;

    /**
     * Patient waiting for a consultation opening.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id",
        referencedColumnName = "user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_waitlist_user")
    )
    private User user;

    /**
     * Target doctor / provider requested by the patient.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "provider_id",
        referencedColumnName = "provider_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_waitlist_provider")
    )
    private Provider provider;

    /**
     * Specific calendar date the patient wishes to consult.
     */
    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    /**
     * Lifecycle status of this waitlist queue record.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private WaitlistStatus status = WaitlistStatus.WAITING;

    /**
     * The appointment slot offered to this candidate upon cancellation.
     * Set when the patient is notified with the CLAIM invitation.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "notified_slot_id",
        referencedColumnName = "appointment_id",
        foreignKey = @ForeignKey(name = "fk_waitlist_notified_slot")
    )
    private Appointment notifiedSlot;

    /**
     * Timestamp when the WhatsApp opening alert was dispatched to this patient.
     */
    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    /**
     * Queue entry creation timestamp (used for strict FIFO priority ordering).
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
}
