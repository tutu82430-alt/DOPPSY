package com.doppsy.service;

import com.doppsy.dto.BlockSlotRequest;
import com.doppsy.dto.BlockSlotResponse;
import com.doppsy.dto.RosterItemResponse;
import com.doppsy.entity.Appointment;
import com.doppsy.entity.Appointment.AppointmentStatus;
import com.doppsy.entity.Provider;
import com.doppsy.exception.ResourceNotFoundException;
import com.doppsy.exception.UnauthorizedAccessException;
import com.doppsy.repository.AppointmentRepository;
import com.doppsy.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service managing doctor roster retrieval, appointment status lifecycle updates,
 * and time-slot blocking for clinical operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorService {

    private final AppointmentRepository appointmentRepository;
    private final ProviderRepository providerRepository;

    private static final Set<AppointmentStatus> VALID_DOCTOR_STATUSES = Set.of(
        AppointmentStatus.COMPLETED,
        AppointmentStatus.CANCELLED,
        AppointmentStatus.NO_SHOW
    );

    /**
     * Retrieves the authenticated doctor's full patient schedule/roster for a specific date,
     * sorted chronologically. Defaults to today if no date is passed.
     *
     * @param providerId the provider's UUID
     * @param date       target calendar date (defaults to today if null)
     * @return chronological list of roster entries
     */
    @Transactional(readOnly = true)
    public List<RosterItemResponse> getRoster(UUID providerId, LocalDate date) {
        Provider provider = findProviderOrThrow(providerId);
        LocalDate targetDate = (date != null) ? date : LocalDate.now();

        LocalDateTime startOfDay = targetDate.atStartOfDay();
        LocalDateTime endOfDay = targetDate.atTime(LocalTime.MAX);

        log.info("[DOCTOR-SERVICE] Fetching roster for provider={}, date={} ({} to {})",
            provider.getFullName(), targetDate, startOfDay, endOfDay);

        List<Appointment> appointments = appointmentRepository.findRosterByProviderAndDate(
            provider.getProviderId(), startOfDay, endOfDay);

        return appointments.stream()
            .map(this::mapToRosterItem)
            .collect(Collectors.toList());
    }

    /**
     * Updates an appointment's status (COMPLETED, CANCELLED, NO_SHOW).
     * Enforces strict provider ownership validation before making any changes.
     *
     * @param appointmentId        UUID of the appointment to update
     * @param requestingProviderId UUID of the authenticated doctor
     * @param newStatus            desired status (COMPLETED, CANCELLED, NO_SHOW)
     * @return updated roster item response
     * @throws ResourceNotFoundException   if the appointment does not exist
     * @throws UnauthorizedAccessException if the appointment belongs to another provider
     * @throws IllegalArgumentException    if the target status is invalid
     */
    @Transactional
    public RosterItemResponse updateAppointmentStatus(
        UUID appointmentId,
        UUID requestingProviderId,
        AppointmentStatus newStatus
    ) {
        if (newStatus == null || !VALID_DOCTOR_STATUSES.contains(newStatus)) {
            throw new IllegalArgumentException(
                "Invalid status update. Allowed statuses: COMPLETED, CANCELLED, NO_SHOW");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Appointment not found with ID: " + appointmentId));

        // Security validation: only the assigned provider can update their appointments
        UUID assignedProviderId = appointment.getProvider().getProviderId();
        if (!assignedProviderId.equals(requestingProviderId)) {
            log.warn("[DOCTOR-SERVICE] Unauthorized status update attempt! ApptId={}, assignedProvider={}, caller={}",
                appointmentId, assignedProviderId, requestingProviderId);
            throw new UnauthorizedAccessException(
                "You are not authorized to update this appointment. It is assigned to another provider.");
        }

        log.info("[DOCTOR-SERVICE] Updating appointment {} status from {} to {}",
            appointmentId, appointment.getStatus(), newStatus);

        appointment.setStatus(newStatus);
        Appointment saved = appointmentRepository.save(appointment);

        return mapToRosterItem(saved);
    }

    /**
     * Blocks out custom time intervals for emergencies, surgery, personal leave, or meetings.
     * Marks existing matching slots as BLOCKED, and creates a BLOCKED slot if none existed,
     * ensuring zero-wait patients cannot book into this window.
     *
     * @param providerId UUID of the provider
     * @param request    block request details (startTime, endTime, reason)
     * @return response summarizing the blocked time window
     */
    @Transactional
    public BlockSlotResponse blockSlots(UUID providerId, BlockSlotRequest request) {
        Provider provider = findProviderOrThrow(providerId);

        LocalDateTime startTime = request.getStartTime();
        LocalDateTime endTime = request.getEndTime() != null
            ? request.getEndTime()
            : startTime.plusHours(1);

        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be strictly after startTime");
        }

        String reason = (request.getReason() != null && !request.getReason().isBlank())
            ? request.getReason().trim()
            : "Blocked by Clinic/Doctor";

        log.info("[DOCTOR-SERVICE] Blocking slots for provider='{}' between {} and {} (reason: '{}')",
            provider.getFullName(), startTime, endTime, reason);

        List<Appointment> existingSlots = appointmentRepository.findSlotsInInterval(
            providerId, startTime, endTime);

        int updatedCount = 0;
        for (Appointment slot : existingSlots) {
            slot.setStatus(AppointmentStatus.BLOCKED);
            appointmentRepository.save(slot);
            updatedCount++;
        }

        // If no slot exists at the exact startTime, create a BLOCKED record to seal the window
        boolean hasExactStartSlot = existingSlots.stream()
            .anyMatch(s -> s.getAppointmentTimestamp().isEqual(startTime));

        if (!hasExactStartSlot) {
            Appointment blockedSlot = new Appointment();
            blockedSlot.setProvider(provider);
            blockedSlot.setUser(null);
            blockedSlot.setAppointmentTimestamp(startTime);
            blockedSlot.setStatus(AppointmentStatus.BLOCKED);
            blockedSlot.setBookingFee(BigDecimal.ZERO);
            appointmentRepository.save(blockedSlot);
            updatedCount++;
        }

        return BlockSlotResponse.builder()
            .providerId(providerId)
            .blockedSlotsCount(updatedCount)
            .startTime(startTime)
            .endTime(endTime)
            .reason(reason)
            .message(String.format("Successfully blocked %d slot(s) between %s and %s.",
                updatedCount, startTime, endTime))
            .build();
    }

    // -----------------------------------------------------------------------
    // Internal Helpers
    // -----------------------------------------------------------------------

    private Provider findProviderOrThrow(UUID providerId) {
        return providerRepository.findById(providerId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Healthcare provider not found with ID: " + providerId));
    }

    private RosterItemResponse mapToRosterItem(Appointment a) {
        String patientName = "Unassigned Slot";
        Integer patientAge = null;
        String patientContact = "—";
        UUID patientUserId = null;

        if (a.getStatus() == AppointmentStatus.BLOCKED) {
            patientName = "Blocked / Reserved Slot";
        } else if (a.getUser() != null) {
            patientName = a.getUser().getFullName();
            patientAge = a.getUser().getAge();
            patientContact = a.getUser().getMobileNumber();
            patientUserId = a.getUser().getUserId();
        }

        return RosterItemResponse.builder()
            .appointmentId(a.getAppointmentId())
            .appointmentTimestamp(a.getAppointmentTimestamp())
            .status(a.getStatus())
            .patientName(patientName)
            .patientAge(patientAge)
            .patientContact(patientContact)
            .patientUserId(patientUserId)
            .bookingFee(a.getBookingFee())
            .createdAt(a.getCreatedAt())
            .build();
    }
}
