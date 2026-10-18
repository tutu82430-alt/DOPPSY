package com.doppsy.service;

import com.doppsy.dto.JoinWaitlistRequest;
import com.doppsy.dto.WaitlistResponse;
import com.doppsy.entity.Appointment;
import com.doppsy.entity.Appointment.AppointmentStatus;
import com.doppsy.entity.Provider;
import com.doppsy.entity.User;
import com.doppsy.entity.WaitlistEntry;
import com.doppsy.entity.WaitlistEntry.WaitlistStatus;
import com.doppsy.event.SlotCancelledEvent;
import com.doppsy.exception.ResourceNotFoundException;
import com.doppsy.repository.AppointmentRepository;
import com.doppsy.repository.ProviderRepository;
import com.doppsy.repository.UserRepository;
import com.doppsy.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Enterprise service driving the Doppsy Smart Waitlist Engine.
 *
 * <p>Orchestrates:
 * 1. Queue ingestion for patients seeking unavailable doctor dates
 * 2. Asynchronous consumption of cancelled slot events via RabbitMQ
 * 3. FIFO candidate selection (top 3 waiting patients)
 * 4. WhatsApp broadcast alerts inviting patients to 'CLAIM' the slot
 * 5. High-concurrency pessimistic database lock resolution on 'CLAIM' replies
 * 6. Automated winner booking confirmation and loser notifications
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final AppointmentRepository appointmentRepository;
    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;
    private final WhatsAppService whatsAppService;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy");

    /**
     * Enqueues a patient onto the waitlist for a specific doctor and date.
     */
    @Transactional
    public WaitlistResponse joinWaitlist(UUID patientId, JoinWaitlistRequest joinRequest) {
        // Guard clauses: input validation
        if (patientId == null) {
            throw new IllegalArgumentException("PatientID must not be null");
        }
        if (joinRequest == null || joinRequest.getProviderId() == null) {
            throw new IllegalArgumentException("ProviderID is required to join waitlist");
        }
        if (joinRequest.getTargetDate() == null) {
            throw new IllegalArgumentException("Target consultation date is required");
        }

        User patient = userRepository.findById(patientId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "userId", patientId));

        Provider doctor = providerRepository.findById(joinRequest.getProviderId())
            .orElseThrow(() -> new ResourceNotFoundException("Provider", "providerId", joinRequest.getProviderId()));

        LocalDate requestedDate = joinRequest.getTargetDate();

        // Guard clause: check for duplicate active waitlist entry
        Optional<WaitlistEntry> existingActiveEntry = waitlistRepository
            .findByUserUserIdAndProviderProviderIdAndTargetDateAndStatusIn(
                patientId,
                doctor.getProviderId(),
                requestedDate,
                List.of(WaitlistStatus.WAITING, WaitlistStatus.NOTIFIED)
            );

        if (existingActiveEntry.isPresent()) {
            log.info("[SMART WAITLIST] Patient {} is already queued on waitlist for Dr. {} on {}",
                patientId, doctor.getFullName(), requestedDate);
            return toWaitlistResponse(existingActiveEntry.get());
        }

        WaitlistEntry newEntry = WaitlistEntry.builder()
            .user(patient)
            .provider(doctor)
            .targetDate(requestedDate)
            .status(WaitlistStatus.WAITING)
            .build();

        WaitlistEntry savedEntry = waitlistRepository.save(newEntry);
        log.info("[SMART WAITLIST] Patient {} successfully joined waitlist for Dr. {} on {} (WaitlistID: {})",
            patientId, doctor.getFullName(), requestedDate, savedEntry.getWaitlistId());

        return toWaitlistResponse(savedEntry);
    }

    /**
     * Consumes a SlotCancelledEvent from RabbitMQ:
     * 1. Queries the database for the top 3 patients on the waitlist for that doctor and date (FIFO).
     * 2. Marks them as NOTIFIED.
     * 3. Sends WhatsApp broadcast: "An earlier slot has opened up. Reply CLAIM to take it."
     */
    @Transactional
    public void processSlotCancellation(SlotCancelledEvent cancellationEvent) {
        // Guard clauses
        if (cancellationEvent == null) {
            log.warn("[SMART WAITLIST] Ignored null SlotCancelledEvent");
            return;
        }
        if (cancellationEvent.getAppointmentId() == null || cancellationEvent.getProviderId() == null) {
            log.warn("[SMART WAITLIST] Discarded invalid cancellation event with missing IDs: {}", cancellationEvent);
            return;
        }

        UUID slotId = cancellationEvent.getAppointmentId();
        UUID providerId = cancellationEvent.getProviderId();
        LocalDate slotDate = cancellationEvent.getSlotDate() != null
            ? cancellationEvent.getSlotDate()
            : cancellationEvent.getAppointmentTimestamp().toLocalDate();

        log.info("[SMART WAITLIST] Processing cancellation for AppointmentID: {}, ProviderID: {}, Date: {}",
            slotId, providerId, slotDate);

        // Step 1: Query top 3 waiting patients for this doctor and date (ordered FIFO by creation timestamp)
        List<WaitlistEntry> topCandidates = waitlistRepository.findTop3CandidatesForSlot(
            providerId,
            slotDate,
            WaitlistStatus.WAITING
        );

        if (topCandidates.isEmpty()) {
            log.info("[SMART WAITLIST] No waiting patients queued for ProviderID: {} on Date: {}. Slot {} remains available.",
                providerId, slotDate, slotId);
            return;
        }

        Appointment openedSlot = appointmentRepository.findById(slotId).orElse(null);

        String doctorDisplayName = cancellationEvent.getDoctorName() != null
            ? cancellationEvent.getDoctorName()
            : "your doctor";

        String slotTimeFormatted = cancellationEvent.getAppointmentTimestamp() != null
            ? cancellationEvent.getAppointmentTimestamp().format(TIME_FMT)
            : "";

        // Step 2 & 3: Notify up to 3 candidates via WhatsApp
        for (WaitlistEntry candidate : topCandidates) {
            candidate.setStatus(WaitlistStatus.NOTIFIED);
            candidate.setNotifiedSlot(openedSlot);
            candidate.setNotifiedAt(LocalDateTime.now());
            waitlistRepository.save(candidate);

            String candidateMobile = candidate.getUser().getMobileNumber();
            if (candidateMobile == null || candidateMobile.isBlank()) {
                log.warn("[SMART WAITLIST] Skipping candidate {} with missing mobile number", candidate.getWaitlistId());
                continue;
            }

            // Prompt mandate message: "An earlier slot has opened up. Reply CLAIM to take it."
            String alertMessage = String.format(
                "Doppsy Smart Waitlist: An earlier slot has opened up with Dr. %s on %s%s. Reply CLAIM to take it.",
                doctorDisplayName,
                slotDate.format(DATE_FMT),
                slotTimeFormatted.isBlank() ? "" : " at " + slotTimeFormatted
            );

            boolean dispatched = whatsAppService.sendMessage(candidateMobile, alertMessage);
            log.info("[SMART WAITLIST ALERT] Dispatched opening alert to Patient Mobile: {} (WaitlistID: {}), Success: {}",
                candidateMobile, candidate.getWaitlistId(), dispatched);
        }

        log.info("[SMART WAITLIST ENGINE] Broadcasted slot {} opening to {} candidate(s) for Dr. {} on {}",
            slotId, topCandidates.size(), doctorDisplayName, slotDate);
    }

    /**
     * Handles the patient's 'CLAIM' reply via WhatsApp:
     * 1. Resolves the active in-flight NOTIFIED waitlist entry for the sender.
     * 2. Acquires a pessimistic write lock (SELECT ... FOR UPDATE) on the target slot.
     * 3. If available, assigns slot to the first responder and sets status to BOOKED.
     * 4. Confirms to the winner and notifies other candidates that the slot was taken.
     * 5. If already taken, informs the responder gracefully and keeps them on the waitlist.
     */
    @Transactional
    public void claimSlotByResponder(String senderMobile) {
        // Guard clause
        if (senderMobile == null || senderMobile.isBlank()) {
            log.warn("[SMART WAITLIST CLAIM] Discarding CLAIM from null/empty mobile number");
            return;
        }

        log.info("[SMART WAITLIST CLAIM] Evaluating CLAIM reply from mobile: {}", senderMobile);

        // Step 1: Find active NOTIFIED waitlist entry for this mobile number
        Optional<WaitlistEntry> activeEntryOpt = waitlistRepository.findActiveNotifiedEntryByMobile(
            senderMobile,
            WaitlistStatus.NOTIFIED
        );

        if (activeEntryOpt.isEmpty()) {
            log.warn("[SMART WAITLIST CLAIM] No active NOTIFIED waitlist entry found for mobile: {}", senderMobile);
            whatsAppService.sendMessage(
                senderMobile,
                "Doppsy Waitlist: No pending slot opening notification was found for your number, or the claim window has expired."
            );
            return;
        }

        WaitlistEntry claimingCandidate = activeEntryOpt.get();
        Appointment targetSlot = claimingCandidate.getNotifiedSlot();

        if (targetSlot == null) {
            log.error("[SMART WAITLIST CLAIM] Candidate {} has NOTIFIED status but null notifiedSlot", claimingCandidate.getWaitlistId());
            whatsAppService.sendMessage(
                senderMobile,
                "Doppsy Waitlist: The slot associated with your notification is no longer active."
            );
            return;
        }

        UUID slotId = targetSlot.getAppointmentId();

        // Step 2: Acquire database row-level lock (PESSIMISTIC_WRITE) on the target slot
        Optional<Appointment> lockedSlotOpt = appointmentRepository.findByIdForUpdate(slotId);

        if (lockedSlotOpt.isEmpty()) {
            log.error("[SMART WAITLIST CLAIM] Target slot {} could not be locked; record not found", slotId);
            whatsAppService.sendMessage(
                senderMobile,
                "Doppsy Waitlist: We encountered an issue securing your slot. Please contact support or try booking via the Doppsy app."
            );
            return;
        }

        Appointment lockedSlot = lockedSlotOpt.get();

        // Step 3: Determine if slot is still available for claiming
        boolean isSlotAvailable = (lockedSlot.getStatus() == AppointmentStatus.AVAILABLE)
            || (lockedSlot.getUser() == null && lockedSlot.getStatus() != AppointmentStatus.COMPLETED);

        if (isSlotAvailable) {
            // =================================================================
            // WINNER PATH: First responder claims the slot atomically!
            // =================================================================
            lockedSlot.setUser(claimingCandidate.getUser());
            lockedSlot.setStatus(AppointmentStatus.BOOKED);
            Appointment bookedSlot = appointmentRepository.save(lockedSlot);

            claimingCandidate.setStatus(WaitlistStatus.CLAIMED);
            waitlistRepository.save(claimingCandidate);

            String doctorName = bookedSlot.getProvider().getFullName();
            String slotTime = bookedSlot.getAppointmentTimestamp().format(TIME_FMT);
            String slotDateStr = bookedSlot.getAppointmentTimestamp().toLocalDate().format(DATE_FMT);

            // Send confirmation to the winning patient
            String winnerMessage = String.format(
                "🎉 Doppsy Priority Confirmed! You have successfully CLAIMED the slot with Dr. %s for %s at %s. Your appointment is now BOOKED.",
                doctorName, slotDateStr, slotTime
            );
            whatsAppService.sendMessage(senderMobile, winnerMessage);

            log.info("[SMART WAITLIST CLAIMED] Slot {} successfully claimed by Patient: {} (Mobile: {}, WaitlistID: {})",
                slotId, claimingCandidate.getUser().getUserId(), senderMobile, claimingCandidate.getWaitlistId());

            // Step 4: Notify the other candidates that the slot was taken
            notifyUnsuccessfulCandidates(slotId, claimingCandidate.getWaitlistId(), doctorName, slotDateStr);

        } else {
            // =================================================================
            // LOSER PATH: Another patient claimed the slot milliseconds earlier
            // =================================================================
            log.info("[SMART WAITLIST RACE] Patient {} was beaten to slot {} (current status: {}, assigned user: {})",
                senderMobile, slotId, lockedSlot.getStatus(),
                lockedSlot.getUser() != null ? lockedSlot.getUser().getUserId() : "none");

            // Re-queue patient back to WAITING status so they retain priority for future cancellations
            claimingCandidate.setStatus(WaitlistStatus.WAITING);
            claimingCandidate.setNotifiedSlot(null);
            waitlistRepository.save(claimingCandidate);

            String doctorName = lockedSlot.getProvider().getFullName();
            String slotTakenNotice = String.format(
                "Doppsy Waitlist Update: Sorry, that slot with Dr. %s has already been claimed by another patient. We have kept you on the priority waitlist for the next opening.",
                doctorName
            );
            whatsAppService.sendMessage(senderMobile, slotTakenNotice);
        }
    }

    /**
     * Notifies all other candidates who were alerted for this slot that another patient claimed it first.
     */
    private void notifyUnsuccessfulCandidates(UUID slotId, UUID winnerWaitlistId, String doctorName, String slotDateStr) {
        List<WaitlistEntry> candidatesForSlot = waitlistRepository.findByNotifiedSlotIdAndStatus(
            slotId,
            WaitlistStatus.NOTIFIED
        );

        int loserCount = 0;
        for (WaitlistEntry otherCandidate : candidatesForSlot) {
            // Guard clause: skip the winner
            if (otherCandidate.getWaitlistId().equals(winnerWaitlistId)) {
                continue;
            }

            // Return unselected patient back to WAITING queue
            otherCandidate.setStatus(WaitlistStatus.WAITING);
            otherCandidate.setNotifiedSlot(null);
            waitlistRepository.save(otherCandidate);

            String otherMobile = otherCandidate.getUser().getMobileNumber();
            if (otherMobile != null && !otherMobile.isBlank()) {
                String notification = String.format(
                    "Doppsy Waitlist Update: The earlier slot with Dr. %s for %s has just been claimed by another patient. You remain at the top of the waitlist for future openings.",
                    doctorName, slotDateStr
                );
                whatsAppService.sendMessage(otherMobile, notification);
                loserCount++;
            }
        }

        log.info("[SMART WAITLIST RESOLVED] Notified {} unselected candidate(s) that slot {} was claimed",
            loserCount, slotId);
    }

    @Transactional(readOnly = true)
    public List<WaitlistResponse> getMyWaitlistEntries(UUID patientId) {
        if (patientId == null) {
            throw new IllegalArgumentException("PatientID must not be null");
        }
        return waitlistRepository.findByUserUserIdOrderByCreatedAtDesc(patientId)
            .stream()
            .map(this::toWaitlistResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<WaitlistResponse> getWaitlistForDoctor(UUID providerId, LocalDate targetDate) {
        if (providerId == null || targetDate == null) {
            throw new IllegalArgumentException("ProviderID and TargetDate are required");
        }
        return waitlistRepository.findByProviderProviderIdAndTargetDateOrderByCreatedAtAsc(providerId, targetDate)
            .stream()
            .map(this::toWaitlistResponse)
            .toList();
    }

    private WaitlistResponse toWaitlistResponse(WaitlistEntry entry) {
        return WaitlistResponse.builder()
            .waitlistId(entry.getWaitlistId())
            .userId(entry.getUser() != null ? entry.getUser().getUserId() : null)
            .patientName(entry.getUser() != null ? entry.getUser().getFullName() : null)
            .patientMobile(entry.getUser() != null ? entry.getUser().getMobileNumber() : null)
            .providerId(entry.getProvider() != null ? entry.getProvider().getProviderId() : null)
            .doctorName(entry.getProvider() != null ? entry.getProvider().getFullName() : null)
            .clinicName(entry.getProvider() != null ? entry.getProvider().getClinicName() : null)
            .targetDate(entry.getTargetDate())
            .status(entry.getStatus())
            .notifiedAt(entry.getNotifiedAt())
            .createdAt(entry.getCreatedAt())
            .build();
    }
}
