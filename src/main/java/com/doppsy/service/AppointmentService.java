package com.doppsy.service;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

        targetAppointment.setStatus(AppointmentStatus.CANCELLED);
        Appointment cancelledAppointment = appointmentRepository.save(targetAppointment);

        log.info("Appointment cancelled successfully. AppointmentID: {}, UserID: {}",
            appointmentId, requestingUserId);

        return toBookingResponse(cancelledAppointment, cancelledAppointment.getProvider());
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
