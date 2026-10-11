package com.doppsy.service;

import com.doppsy.entity.Appointment;
import com.doppsy.entity.Appointment.AppointmentStatus;
import com.doppsy.repository.AppointmentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Service processing incoming WhatsApp webhook messages for the Doppsy concierge.
 *
 * <p>Handles:
 * <ul>
 *   <li>"CANCEL" keyword (case-insensitive) -> cancels the patient's most recent BOOKED appointment.</li>
 *   <li>Fallback inquiries -> notifies the patient that a human concierge assistant will reply.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppWebhookService {

    private final AppointmentRepository appointmentRepository;
    private final WhatsAppService whatsappService;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("EEE, dd MMM 'at' hh:mm a");

    /**
     * Processes an incoming WhatsApp payload (supports both Meta Cloud API webhook
     * format and simplified test payloads).
     *
     * @param rawPayload raw JSON string received from the webhook POST request
     */
    @Transactional
    public void processWebhookPayload(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            log.warn("[WHATSAPP WEBHOOK] Received empty payload");
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(rawPayload);

            // 1. Direct/Simplified test payload: { "from": "...", "text": "CANCEL" }
            if (root.has("from")) {
                String from = root.path("from").asText();
                String text = root.has("text")
                    ? (root.path("text").isObject() ? root.path("text").path("body").asText() : root.path("text").asText())
                    : "";
                handleMessage(from, text);
                return;
            }

            // 2. Meta WhatsApp Cloud API nested webhook format
            JsonNode entryNode = root.path("entry");
            if (entryNode.isArray() && entryNode.size() > 0) {
                for (JsonNode entry : entryNode) {
                    JsonNode changes = entry.path("changes");
                    if (changes.isArray()) {
                        for (JsonNode change : changes) {
                            JsonNode value = change.path("value");
                            JsonNode messages = value.path("messages");
                            if (messages.isArray() && messages.size() > 0) {
                                for (JsonNode message : messages) {
                                    String from = message.path("from").asText();
                                    String type = message.path("type").asText();
                                    String text = "";

                                    if ("text".equalsIgnoreCase(type) && message.has("text")) {
                                        text = message.path("text").path("body").asText();
                                    } else if (message.has("button")) {
                                        text = message.path("button").path("text").asText();
                                    }

                                    handleMessage(from, text);
                                }
                            }
                        }
                    }
                }
            } else {
                log.info("[WHATSAPP WEBHOOK] Payload received with no actionable messages: {}", rawPayload);
            }
        } catch (Exception e) {
            log.error("[WHATSAPP WEBHOOK ERROR] Failed to parse and process webhook payload: {}", e.getMessage(), e);
        }
    }

    /**
     * Executes the conversational logic for a single message from a patient.
     *
     * @param senderMobile mobile number of the patient
     * @param textBody     text sent by the patient
     */
    @Transactional
    public void handleMessage(String senderMobile, String textBody) {
        if (senderMobile == null || senderMobile.isBlank()) {
            log.warn("[WHATSAPP WEBHOOK] Received message with missing sender mobile");
            return;
        }

        String trimmedText = (textBody != null) ? textBody.trim() : "";
        log.info("[WHATSAPP INBOUND] Processing message from {}: '{}'", senderMobile, trimmedText);

        // ── Case 1: CANCEL command ──────────────────────────────────────────
        if ("CANCEL".equalsIgnoreCase(trimmedText)) {
            handleCancellation(senderMobile);
            return;
        }

        // ── Case 2: Fallback inquiry response ───────────────────────────────
        String fallbackMsg = "DOPSY Concierge: We have received your message. A human assistant will reply shortly.";
        whatsappService.sendMessage(senderMobile, fallbackMsg);
    }

    /**
     * Looks up the patient's most recent BOOKED appointment, marks it CANCELLED,
     * and sends a WhatsApp confirmation.
     */
    private void handleCancellation(String senderMobile) {
        Optional<Appointment> appointmentOpt =
            appointmentRepository.findMostRecentBookedByMobile(senderMobile);

        if (appointmentOpt.isPresent()) {
            Appointment appointment = appointmentOpt.get();
            appointment.setStatus(AppointmentStatus.CANCELLED);
            appointmentRepository.save(appointment);

            String doctorName = appointment.getProvider().getFullName();
            String formattedTime = appointment.getAppointmentTimestamp().format(TIME_FMT);

            log.info("[WHATSAPP CONCIERGE] Cancelled appointment {} for sender {} with {}",
                appointment.getAppointmentId(), senderMobile, doctorName);

            String confirmationMsg = String.format(
                "Doppsy Concierge: Your appointment with %s scheduled for %s has been CANCELLED successfully. Reply BOOK if you wish to reschedule.",
                doctorName, formattedTime
            );

            whatsappService.sendMessage(senderMobile, confirmationMsg);
        } else {
            log.warn("[WHATSAPP CONCIERGE] No active BOOKED appointment found to cancel for mobile: {}", senderMobile);
            String noAppointmentMsg =
                "Doppsy Concierge: We could not find any active BOOKED appointment linked to your number. If you need assistance, our support team will connect with you shortly.";
            whatsappService.sendMessage(senderMobile, noAppointmentMsg);
        }
    }
}
