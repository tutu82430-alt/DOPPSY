package com.doppsy.service;

import com.doppsy.dto.StructuredPrescriptionDto;
import com.doppsy.dto.BookingRequest;
import com.doppsy.dto.BookingResponse;
import com.doppsy.dto.VerifyPaymentRequest;
import com.doppsy.entity.Appointment;
import com.doppsy.entity.Appointment.AppointmentStatus;
import com.doppsy.entity.Provider;
import com.doppsy.entity.User;
import com.doppsy.exception.PaymentSignatureMismatchException;
import com.doppsy.exception.ResourceNotFoundException;
import com.doppsy.exception.SlotAlreadyTakenException;
import com.doppsy.exception.UnauthorizedAccessException;
import com.doppsy.repository.AppointmentRepository;
import com.doppsy.repository.ProviderRepository;
import com.doppsy.repository.UserRepository;
import com.doppsy.config.RabbitMQConfig;
import com.doppsy.event.SlotCancelledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;
    private final WhatsAppService whatsAppService;
    private final PaymentService paymentService;
    private final PdfGeneratorService pdfGeneratorService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${doppsy.rabbitmq.exchange:" + RabbitMQConfig.EXCHANGE_SLOT_EVENTS + "}")
    private String slotExchangeName = RabbitMQConfig.EXCHANGE_SLOT_EVENTS;

    @Value("${doppsy.rabbitmq.routing-key:" + RabbitMQConfig.ROUTING_KEY_SLOT_CANCELLED + "}")
    private String slotRoutingKey = RabbitMQConfig.ROUTING_KEY_SLOT_CANCELLED;

    /**
     * Step 1: Reserves a consultation slot as PENDING_PAYMENT and creates a Razorpay order
     * for the ₹200 booking deposit.
     */
    @Transactional
    public BookingResponse bookAppointment(UUID patientId, BookingRequest bookingRequestPayload) {
        // Guard clauses: ensure required entities exist
        if (patientId == null) {
            throw new IllegalArgumentException("PatientID must not be null");
        }
        if (bookingRequestPayload == null || bookingRequestPayload.getProviderId() == null) {
            throw new IllegalArgumentException("ProviderID is required for slot reservation");
        }
        if (bookingRequestPayload.getAppointmentTimestamp() == null) {
            throw new IllegalArgumentException("Appointment timestamp is required");
        }

        User patientUser = userRepository.findById(patientId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "userId", patientId));

        Provider healthcareProvider = providerRepository.findById(bookingRequestPayload.getProviderId())
            .orElseThrow(() -> new ResourceNotFoundException("Provider", "providerId", bookingRequestPayload.getProviderId()));

        LocalDateTime requestedSlotTime = bookingRequestPayload.getAppointmentTimestamp();

        log.info("Initiating ₹200 deposit for PatientID: {}, ProviderID: {}, Slot: {}",
            patientId, healthcareProvider.getProviderId(), requestedSlotTime);

        // Note: Pessimistic locking prevents double-booking race condition during high concurrency
        Optional<Appointment> existingSlotReservation = appointmentRepository
            .findByProviderAndTimestampForUpdate(healthcareProvider.getProviderId(), requestedSlotTime);

        Appointment pendingBookingTxn;

        if (existingSlotReservation.isPresent()) {
            Appointment existingSlot = existingSlotReservation.get();

            // Guard clause: reject immediately if slot is already held or booked
            if (existingSlot.getStatus() != AppointmentStatus.AVAILABLE) {
                log.warn("Slot collision: ProviderID: {}, Time: {} already in status: {}",
                    healthcareProvider.getProviderId(), requestedSlotTime, existingSlot.getStatus());
                throw new SlotAlreadyTakenException(String.format(
                    "The consultation slot at %s with Dr. %s is no longer available (current status: %s)",
                    requestedSlotTime, healthcareProvider.getFullName(), existingSlot.getStatus()));
            }

            existingSlot.setUser(patientUser);
            existingSlot.setStatus(AppointmentStatus.PENDING_PAYMENT);
            existingSlot.setBookingFee(calculateDepositFee(healthcareProvider));
            pendingBookingTxn = appointmentRepository.save(existingSlot);
        } else {
            // No pre-created schedule slot; allocate on-demand with PENDING_PAYMENT lock
            pendingBookingTxn = new Appointment();
            pendingBookingTxn.setUser(patientUser);
            pendingBookingTxn.setProvider(healthcareProvider);
            pendingBookingTxn.setAppointmentTimestamp(requestedSlotTime);
            pendingBookingTxn.setStatus(AppointmentStatus.PENDING_PAYMENT);
            pendingBookingTxn.setBookingFee(calculateDepositFee(healthcareProvider));
            pendingBookingTxn = appointmentRepository.save(pendingBookingTxn);
        }

        // Delegate Razorpay order generation to PaymentService
        String razorpayOrderId = paymentService.createBookingDepositOrder(
            pendingBookingTxn.getAppointmentId(),
            patientId,
            healthcareProvider.getProviderId(),
            patientUser.getFullName()
        );

        pendingBookingTxn.setRazorpayOrderId(razorpayOrderId);
        pendingBookingTxn = appointmentRepository.save(pendingBookingTxn);

        BookingResponse bookingInitiationResponse = toBookingResponse(pendingBookingTxn, healthcareProvider);
        bookingInitiationResponse.setRazorpayOrderId(razorpayOrderId);
        bookingInitiationResponse.setRazorpayKeyId(paymentService.getRazorpayKeyId());

        return bookingInitiationResponse;
    }

    /**
     * Step 2: Verifies Razorpay payment signature via HMAC SHA256 and promotes appointment to BOOKED.
     */
    @Transactional
    public BookingResponse verifyPaymentAndConfirm(VerifyPaymentRequest paymentVerificationPayload) {
        // Guard clause: validate request payload
        if (paymentVerificationPayload == null) {
            throw new IllegalArgumentException("Payment verification payload must not be null");
        }

        String razorpayOrderId = paymentVerificationPayload.getRazorpayOrderId();
        String razorpayPaymentId = paymentVerificationPayload.getRazorpayPaymentId();
        String razorpaySignature = paymentVerificationPayload.getRazorpaySignature();

        Appointment pendingBookingTxn = appointmentRepository.findByRazorpayOrderId(razorpayOrderId)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment", "razorpayOrderId", razorpayOrderId));

        // Guard clause: ensure appointment is awaiting payment verification
        if (pendingBookingTxn.getStatus() != AppointmentStatus.PENDING_PAYMENT) {
            throw new SlotAlreadyTakenException(String.format(
                "Appointment %s cannot be verified: current status is '%s', expected 'PENDING_PAYMENT'",
                pendingBookingTxn.getAppointmentId(), pendingBookingTxn.getStatus()));
        }

        // Cryptographic HMAC SHA256 verification (throws PaymentSignatureMismatchException on failure)
        paymentService.verifyPaymentSignature(razorpayOrderId, razorpayPaymentId, razorpaySignature);

        // Update appointment status to confirmed BOOKED
        pendingBookingTxn.setStatus(AppointmentStatus.BOOKED);
        pendingBookingTxn.setRazorpayPaymentId(razorpayPaymentId);
        Appointment confirmedBooking = appointmentRepository.save(pendingBookingTxn);

        log.info("Payment verified and slot confirmed for AppointmentID: {}, RazorpayOrderID: {}, PaymentID: {}",
            confirmedBooking.getAppointmentId(), razorpayOrderId, razorpayPaymentId);

        // Async trigger prevents external WhatsApp API latency from blocking client response
        dispatchAsyncWhatsAppConfirmation(confirmedBooking);

        return toBookingResponse(confirmedBooking, confirmedBooking.getProvider());
    }

    /**
     * Cancels an appointment. Allows cancellation of BOOKED or PENDING_PAYMENT slots.
     */
    @Transactional
    public BookingResponse cancelAppointment(UUID appointmentId, UUID requestingUserId) {
        if (appointmentId == null || requestingUserId == null) {
            throw new IllegalArgumentException("AppointmentID and RequestingUserID are required to cancel");
        }

        Appointment targetAppointment = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment", "appointmentId", appointmentId));

        // Guard clause: verify ownership or admin privileges
        if (targetAppointment.getUser() != null && !targetAppointment.getUser().getUserId().equals(requestingUserId)) {
            throw new UnauthorizedAccessException("You are not authorized to cancel this appointment");
        }

        if (targetAppointment.getStatus() != AppointmentStatus.BOOKED
                && targetAppointment.getStatus() != AppointmentStatus.PENDING_PAYMENT) {
            throw new IllegalStateException(String.format(
                "Cannot cancel appointment with status '%s'. Only BOOKED or PENDING_PAYMENT can be cancelled.",
                targetAppointment.getStatus()));
        }

        targetAppointment.setStatus(AppointmentStatus.AVAILABLE);
        UUID previousUserId = targetAppointment.getUser() != null ? targetAppointment.getUser().getUserId() : requestingUserId;
        targetAppointment.setUser(null);
        Appointment cancelledAppointment = appointmentRepository.save(targetAppointment);

        log.info("Appointment cancelled and released to schedule. AppointmentID: {}, UserID: {}",
            appointmentId, requestingUserId);

        // Publish SlotCancelledEvent to RabbitMQ to alert waitlisted patients
        publishSlotCancelledEvent(cancelledAppointment, previousUserId);

        return toBookingResponse(cancelledAppointment, cancelledAppointment.getProvider());
    }

    /**
     * Cancels the patient's most recent BOOKED appointment via the WhatsApp webhook,
     * releases the slot back to AVAILABLE, and publishes a SlotCancelledEvent to RabbitMQ.
     */
    @Transactional
    public void cancelAppointmentViaWebhook(String senderMobile) {
        if (senderMobile == null || senderMobile.isBlank()) {
            log.warn("[WHATSAPP CANCELLATION] Discarded cancellation with null or blank mobile number");
            return;
        }

        Optional<Appointment> bookedAppointmentOpt = appointmentRepository.findMostRecentBookedByMobile(senderMobile);

        if (bookedAppointmentOpt.isEmpty()) {
            log.warn("[WHATSAPP CANCELLATION] No active BOOKED appointment found to cancel for mobile: {}", senderMobile);
            whatsAppService.sendMessage(
                senderMobile,
                "Doppsy Concierge: We could not find any active BOOKED appointment linked to your number. If you need assistance, reply HELP."
            );
            return;
        }

        Appointment appointment = bookedAppointmentOpt.get();
        UUID previousUserId = appointment.getUser() != null ? appointment.getUser().getUserId() : null;
        String doctorName = appointment.getProvider().getFullName();
        String formattedSlotTime = appointment.getAppointmentTimestamp().format(
            DateTimeFormatter.ofPattern("EEE, dd MMM 'at' hh:mm a")
        );

        // Release slot back to AVAILABLE for waitlist candidate claiming
        appointment.setStatus(AppointmentStatus.AVAILABLE);
        appointment.setUser(null);
        appointment.setRazorpayOrderId(null);
        appointment.setRazorpayPaymentId(null);
        Appointment releasedAppointment = appointmentRepository.save(appointment);

        log.info("[WHATSAPP CANCELLATION] Cancelled and released appointment {} for sender {} with Dr. {}",
            releasedAppointment.getAppointmentId(), senderMobile, doctorName);

        // Publish SlotCancelledEvent to RabbitMQ exchange
        publishSlotCancelledEvent(releasedAppointment, previousUserId);

        // Acknowledge patient on WhatsApp
        String confirmationMsg = String.format(
            "Doppsy Concierge: Your appointment with Dr. %s scheduled for %s has been CANCELLED successfully. Reply BOOK if you wish to reschedule.",
            doctorName, formattedSlotTime
        );
        whatsAppService.sendMessage(senderMobile, confirmationMsg);
    }

    /**
     * Publishes a SlotCancelledEvent to the RabbitMQ exchange to trigger the Smart Waitlist Engine.
     */
    public void publishSlotCancelledEvent(Appointment appointment, UUID previousUserId) {
        if (appointment == null || appointment.getProvider() == null) {
            log.warn("[RABBITMQ WAITLIST] Cannot publish SlotCancelledEvent: appointment or provider is null");
            return;
        }

        try {
            SlotCancelledEvent event = SlotCancelledEvent.builder()
                .appointmentId(appointment.getAppointmentId())
                .providerId(appointment.getProvider().getProviderId())
                .doctorName(appointment.getProvider().getFullName())
                .clinicName(appointment.getProvider().getClinicName())
                .appointmentTimestamp(appointment.getAppointmentTimestamp())
                .slotDate(appointment.getAppointmentTimestamp().toLocalDate())
                .cancelledByUserId(previousUserId)
                .cancelledAt(LocalDateTime.now())
                .build();

            rabbitTemplate.convertAndSend(slotExchangeName, slotRoutingKey, event);
            log.info("[RABBITMQ WAITLIST] Published SlotCancelledEvent for AppointmentID: {}, ProviderID: {}, Date: {} to exchange: '{}'",
                appointment.getAppointmentId(), appointment.getProvider().getProviderId(), event.getSlotDate(), slotExchangeName);
        } catch (Exception amqpEx) {
            log.error("[RABBITMQ WAITLIST ERROR] Failed to publish SlotCancelledEvent for AppointmentID: {}: {}",
                appointment.getAppointmentId(), amqpEx.getMessage(), amqpEx);
        }
    }


    @Transactional(readOnly = true)
    public List<BookingResponse> getMyAppointments(UUID patientId) {
        if (patientId == null) {
            throw new IllegalArgumentException("PatientID must not be null");
        }
        return appointmentRepository.findByUserUserIdOrderByAppointmentTimestampDesc(patientId)
            .stream()
            .map(appt -> toBookingResponse(appt, appt.getProvider()))
            .toList();
    }

    /**
     * Sends WhatsApp booking confirmation in background thread.
     */
    @Async
    protected void dispatchAsyncWhatsAppConfirmation(Appointment appointment) {
        try {
            User patientUser = appointment.getUser();
            Provider provider = appointment.getProvider();
            if (patientUser != null && patientUser.getMobileNumber() != null) {
                String confirmationMessage = String.format(
                    "✅ Doppsy Zero-Wait Booking Confirmed!\n\n" +
                    "Doctor: Dr. %s\n" +
                    "Clinic: %s\n" +
                    "Date & Time: %s\n" +
                    "Booking Deposit Paid: ₹%s (Razorpay)\n\n" +
                    "Your zero-wait priority slot has been secured. Reply 'CANCEL' anytime to release your slot.",
                    provider.getFullName(),
                    provider.getClinicName(),
                    appointment.getAppointmentTimestamp(),
                    appointment.getBookingFee() != null ? appointment.getBookingFee() : "200"
                );
                whatsAppService.sendTextMessage(patientUser.getMobileNumber(), confirmationMessage);
                log.info("WhatsApp confirmation dispatched asynchronously to: {}", patientUser.getMobileNumber());
            }
        } catch (Exception notificationEx) {
            log.error("Failed to dispatch WhatsApp confirmation for AppointmentID: {}, Error: {}",
                appointment.getAppointmentId(), notificationEx.getMessage());
        }
    }

    /**
     * AI Voice Prescription Delivery Flow:
     * 1. Identifies the doctor's most recently COMPLETED appointment.
     * 2. Generates the branded PDF prescription via PdfGeneratorService.
     * 3. Uploads the PDF to Meta WhatsApp Cloud API to obtain a media_id.
     * 4. Immediately routes the PDF document message to the patient on WhatsApp.
     * 5. Confirms delivery back to the prescribing doctor.
     */
    @Transactional
    public void processAndDeliverVoicePrescription(Provider doctorProvider, StructuredPrescriptionDto prescriptionPayload) {
        // Guard clauses
        if (doctorProvider == null) {
            throw new IllegalArgumentException("Doctor provider must not be null for prescription delivery");
        }
        if (prescriptionPayload == null) {
            throw new IllegalArgumentException("Structured prescription payload must not be null");
        }

        UUID providerId = doctorProvider.getProviderId();
        log.info("[VOICE-EMR-DELIVERY] Locating most recent completed appointment for Doctor: '{}' (ProviderID: {})",
            doctorProvider.getFullName(), providerId);

        // Primary lookup: most recently COMPLETED appointment
        Optional<Appointment> targetAppointmentOpt = appointmentRepository.findMostRecentCompletedByProviderId(providerId);

        // Fallback: in-flight target if appointment was completed in-person right before voice note dictation
        if (targetAppointmentOpt.isEmpty()) {
            log.warn("[VOICE-EMR-DELIVERY] No COMPLETED appointment found for ProviderID: {}. Checking active target appointments...", providerId);
            targetAppointmentOpt = appointmentRepository.findMostRecentPrescriptionTargetAppointment(providerId);
        }

        if (targetAppointmentOpt.isEmpty()) {
            log.error("[VOICE-EMR-DELIVERY] Delivery aborted: No active or completed appointment found for Doctor: {}", doctorProvider.getFullName());
            if (doctorProvider.getMobileNumber() != null) {
                whatsAppService.sendMessage(doctorProvider.getMobileNumber(),
                    "⚠️ Doppsy Concierge: We transcribed your voice prescription, but could not find a recently completed appointment to attach it to. Please ensure the patient's appointment is completed.");
            }
            return;
        }

        Appointment targetAppointment = targetAppointmentOpt.get();
        User patientUser = targetAppointment.getUser();

        if (patientUser == null || patientUser.getMobileNumber() == null) {
            log.error("[VOICE-EMR-DELIVERY] Patient mobile number missing for AppointmentID: {}", targetAppointment.getAppointmentId());
            return;
        }

        // 1. Generate professional PDF prescription
        byte[] pdfBytes = pdfGeneratorService.renderPrescriptionPdf(prescriptionPayload, targetAppointment);
        pdfGeneratorService.generatePrescriptionPdfFile(prescriptionPayload, targetAppointment); // persist temporary file on server

        // 2. Upload generated PDF back to Meta API to get a new media_id
        String documentFilename = String.format("Prescription_Dr_%s_%s.pdf",
            doctorProvider.getFullName().replace(" ", "_"),
            targetAppointment.getAppointmentId().toString().substring(0, 8));

        String uploadedMediaId = whatsAppService.uploadDocumentMedia(pdfBytes, documentFilename, "application/pdf");

        // 3. Immediately send as WhatsApp Document message to that patient
        String patientCaption = String.format(
            "📋 Official Digital Prescription from Dr. %s (%s) for your consultation on %s.",
            doctorProvider.getFullName(),
            doctorProvider.getClinicName() != null ? doctorProvider.getClinicName() : "Doppsy Healthcare",
            targetAppointment.getAppointmentTimestamp() != null ? targetAppointment.getAppointmentTimestamp().toLocalDate() : "today"
        );

        boolean isDelivered = whatsAppService.sendDocumentMessage(
            patientUser.getMobileNumber(),
            uploadedMediaId,
            patientCaption,
            documentFilename
        );

        if (isDelivered) {
            log.info("Successfully generated and routed AI prescription for AppointmentID: {}", targetAppointment.getAppointmentId());

            // 4. Notify doctor of successful delivery
            if (doctorProvider.getMobileNumber() != null) {
                whatsAppService.sendMessage(doctorProvider.getMobileNumber(), String.format(
                    "✅ AI Prescription Generated & Delivered!\n\nPatient: %s\nDiagnosis: %s\nRx sent to: %s via WhatsApp.",
                    patientUser.getFullName(),
                    prescriptionPayload.getDiagnosis(),
                    patientUser.getMobileNumber()
                ));
            }
        } else {
            log.error("[VOICE-EMR-DELIVERY] Failed to deliver document message for AppointmentID: {}", targetAppointment.getAppointmentId());
        }
    }

    private BigDecimal calculateDepositFee(Provider provider) {
        // Standard ₹200 booking deposit across platform tiers
        return new BigDecimal("200.00");
    }

    private BookingResponse toBookingResponse(Appointment appointment, Provider provider) {
        return BookingResponse.builder()
            .appointmentId(appointment.getAppointmentId())
            .providerId(provider != null ? provider.getProviderId() : null)
            .providerName(provider != null ? provider.getFullName() : "Doppsy Healthcare Partner")
            .clinicName(provider != null ? provider.getClinicName() : "Doppsy Partner Clinic")
            .appointmentTimestamp(appointment.getAppointmentTimestamp())
            .status(appointment.getStatus())
            .bookingFee(appointment.getBookingFee())
            .createdAt(appointment.getCreatedAt())
            .razorpayOrderId(appointment.getRazorpayOrderId())
            .build();
    }
}
