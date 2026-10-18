package com.doppsy.service;

import com.doppsy.dto.StructuredPrescriptionDto;
import com.doppsy.entity.Appointment;
import com.doppsy.entity.Appointment.AppointmentStatus;
import com.doppsy.entity.Provider;
import com.doppsy.repository.AppointmentRepository;
import com.doppsy.repository.ProviderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Enterprise service processing inbound WhatsApp webhook events:
 * 1. AI Voice Prescription (Voice Notes / Audio from registered Doctors) -> Whisper + GPT-4o-mini -> PDF -> Patient delivery
 * 2. Patient Conversational Commands (e.g. "CANCEL" keyword) -> Automated slot cancellation
 * 3. General Fallback Inquiries -> Concierge helpdesk routing
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppWebhookService {

    private final AppointmentRepository appointmentRepository;
    private final ProviderRepository providerRepository;
    private final WhatsAppService whatsappService;
    private final VoicePrescriptionService voicePrescriptionService;
    private final AppointmentService appointmentService;
    private final WaitlistService waitlistService;
    private final ObjectMapper objectMapper;


    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("EEE, dd MMM 'at' hh:mm a");

    /**
     * Parses the incoming Meta webhook payload, identifying message types (audio vs. text).
     */
    @Transactional
    public void processWebhookPayload(String rawWebhookPayload) {
        // Guard clause: reject blank payloads immediately
        if (rawWebhookPayload == null || rawWebhookPayload.isBlank()) {
            log.warn("[WHATSAPP WEBHOOK] Rejected empty incoming payload");
            return;
        }

        try {
            JsonNode payloadRoot = objectMapper.readTree(rawWebhookPayload);

            // 1. Direct / Simplified test payload compatibility (e.g. Postman or direct integration tests)
            if (payloadRoot.has("from")) {
                String senderMobile = payloadRoot.path("from").asText();
                String messageType = payloadRoot.path("type").asText("text");

                if ("audio".equalsIgnoreCase(messageType) || payloadRoot.has("media_id") || payloadRoot.has("audio")) {
                    String mediaId = payloadRoot.has("media_id")
                        ? payloadRoot.path("media_id").asText()
                        : payloadRoot.path("audio").path("id").asText();
                    handleAudioMessage(senderMobile, mediaId);
                    return;
                }

                String textBody = payloadRoot.has("text")
                    ? (payloadRoot.path("text").isObject() ? payloadRoot.path("text").path("body").asText() : payloadRoot.path("text").asText())
                    : "";
                handleTextMessage(senderMobile, textBody);
                return;
            }

            // 2. Standard Meta WhatsApp Cloud API nested webhook format
            JsonNode entryArray = payloadRoot.path("entry");
            if (!entryArray.isArray() || entryArray.isEmpty()) {
                log.info("[WHATSAPP WEBHOOK] Non-message webhook event received: {}", rawWebhookPayload);
                return;
            }

            for (JsonNode entryItem : entryArray) {
                JsonNode changesArray = entryItem.path("changes");
                if (!changesArray.isArray()) continue;

                for (JsonNode changeItem : changesArray) {
                    JsonNode messageValue = changeItem.path("value");
                    JsonNode messagesArray = messageValue.path("messages");
                    if (!messagesArray.isArray() || messagesArray.isEmpty()) continue;

                    for (JsonNode messageNode : messagesArray) {
                        String senderMobile = messageNode.path("from").asText();
                        String messageType = messageNode.path("type").asText();

                        // Branch A: Audio / Voice Note message
                        if ("audio".equalsIgnoreCase(messageType) || "voice".equalsIgnoreCase(messageType)) {
                            String mediaId = messageNode.path("audio").path("id").asText();
                            handleAudioMessage(senderMobile, mediaId);
                            continue;
                        }

                        // Branch B: Text / Button message
                        String textBody = "";
                        if ("text".equalsIgnoreCase(messageType) && messageNode.has("text")) {
                            textBody = messageNode.path("text").path("body").asText();
                        } else if (messageNode.has("button")) {
                            textBody = messageNode.path("button").path("text").asText();
                        }

                        handleTextMessage(senderMobile, textBody);
                    }
                }
            }

        } catch (Exception webhookException) {
            log.error("[WHATSAPP WEBHOOK ERROR] Failed to process payload: {}", webhookException.getMessage(), webhookException);
        }
    }

    /**
     * Handles incoming audio messages (voice notes).
     * If the audio message is from a registered doctor, triggers the AI Voice Prescription flow.
     */
    @Transactional
    public void handleAudioMessage(String senderMobile, String metaMediaId) {
        // Guard clauses
        if (senderMobile == null || senderMobile.isBlank()) {
            log.warn("[WHATSAPP AUDIO] Discarding audio note with missing sender");
            return;
        }
        if (metaMediaId == null || metaMediaId.isBlank()) {
            log.warn("[WHATSAPP AUDIO] Discarding audio note from {} due to missing Meta media_id", senderMobile);
            return;
        }

        log.info("[WHATSAPP AUDIO] Received inbound audio message from: {}, Meta MediaID: {}", senderMobile, metaMediaId);

        // Step 1: Verify whether the sender is a registered Doctor / Healthcare Provider
        Optional<Provider> registeredDoctorOpt = providerRepository.findByMatchingMobile(senderMobile);

        if (registeredDoctorOpt.isEmpty()) {
            log.info("[WHATSAPP AUDIO] Sender {} is not a registered doctor. Sending patient audio acknowledgment.", senderMobile);
            whatsappService.sendMessage(senderMobile,
                "Doppsy Concierge: We received your audio note. A healthcare representative will review your message shortly.");
            return;
        }

        Provider doctorProvider = registeredDoctorOpt.get();
        log.info("[VOICE-EMR] Authenticated voice prescription from Dr. {} (ProviderID: {}, Mobile: {})",
            doctorProvider.getFullName(), doctorProvider.getProviderId(), senderMobile);

        try {
            // Acknowledge receipt immediately to doctor
            whatsappService.sendMessage(senderMobile,
                "🎙️ Doppsy AI EMR: Audio received! Transcribing consultation and generating patient prescription PDF...");

            // Step 2: Download raw .ogg audio binary from Meta Cloud API
            byte[] voiceNoteBytes = whatsappService.downloadMedia(metaMediaId);

            // Step 3: Run AI Voice Prescription Pipeline (Whisper -> GPT-4o-mini structuring)
            StructuredPrescriptionDto structuredPrescription = voicePrescriptionService.processVoiceNote(voiceNoteBytes);

            // Step 4: Route PDF generation and delivery to the doctor's recent completed patient appointment
            appointmentService.processAndDeliverVoicePrescription(doctorProvider, structuredPrescription);

        } catch (Exception voiceProcessingException) {
            log.error("[VOICE-EMR ERROR] Failed to process voice prescription for Dr. {}: {}",
                doctorProvider.getFullName(), voiceProcessingException.getMessage(), voiceProcessingException);

            whatsappService.sendMessage(senderMobile,
                "⚠️ Doppsy AI EMR Error: Unable to process your voice prescription. Please try recording again or contact clinical IT support.");
        }
    }

    /**
     * Executes conversational logic for inbound patient text messages.
     */
    @Transactional
    public void handleTextMessage(String senderMobile, String textBody) {
        if (senderMobile == null || senderMobile.isBlank()) {
            log.warn("[WHATSAPP TEXT] Missing sender mobile");
            return;
        }

        String sanitizedText = (textBody != null) ? textBody.trim() : "";
        log.info("[WHATSAPP INBOUND] Processing text from {}: '{}'", senderMobile, sanitizedText);

        // Case 1: CANCEL command -> delegates to AppointmentService to release slot and publish SlotCancelledEvent to RabbitMQ
        if ("CANCEL".equalsIgnoreCase(sanitizedText)) {
            appointmentService.cancelAppointmentViaWebhook(senderMobile);
            return;
        }

        // Case 2: CLAIM command -> Smart Waitlist Engine atomic acquisition with database lock
        if ("CLAIM".equalsIgnoreCase(sanitizedText)) {
            waitlistService.claimSlotByResponder(senderMobile);
            return;
        }

        // Case 3: General inquiry fallback
        String fallbackResponse = "Doppsy Concierge: We have received your message. Our clinical care team will reply shortly. Reply 'CANCEL' to cancel an appointment or 'CLAIM' to secure an opened waitlist slot.";
        whatsappService.sendMessage(senderMobile, fallbackResponse);
    }
}

