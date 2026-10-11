package com.doppsy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Service responsible for dispatching outbound messages via Meta's WhatsApp Cloud API.
 * In development or testing with mock credentials, it logs messages directly to console.
 */
@Slf4j
@Service
public class WhatsAppService {

    private final String phoneNumberId;
    private final String accessToken;
    private final String apiUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WhatsAppService(
        @Value("${doppsy.whatsapp.phone-number-id}") String phoneNumberId,
        @Value("${doppsy.whatsapp.access-token}") String accessToken,
        @Value("${doppsy.whatsapp.api-url}") String apiUrl,
        ObjectMapper objectMapper
    ) {
        this.phoneNumberId = phoneNumberId;
        this.accessToken = accessToken;
        this.apiUrl = apiUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /**
     * Sends a text message to the specified recipient phone number.
     *
     * @param to   the recipient's mobile number (E.164 without '+' or standard digits)
     * @param body the message text body
     * @return boolean indicating whether the message was dispatched or logged
     */
    public boolean sendMessage(String to, String body) {
        String cleanNumber = cleanPhoneNumber(to);

        log.info("[WHATSAPP OUTBOUND] To: {} | Body: '{}'", cleanNumber, body);

        // If mock access token or missing, log and return true
        if (accessToken == null || accessToken.isBlank() || accessToken.startsWith("mock_")) {
            log.info("[WHATSAPP SIMULATION] Successfully simulated outbound message to: {}", cleanNumber);
            return true;
        }

        try {
            String endpoint = String.format("%s/%s/messages", apiUrl, phoneNumberId);

            Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", cleanNumber,
                "type", "text",
                "text", Map.of(
                    "preview_url", false,
                    "body", body
                )
            );

            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[WHATSAPP API SUCCESS] Status: {} | Response: {}", response.statusCode(), response.body());
                return true;
            } else {
                log.error("[WHATSAPP API ERROR] Status: {} | Error: {}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("[WHATSAPP EXCEPTION] Failed to send WhatsApp message to {}: {}", cleanNumber, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Cleans up mobile number format (removes spaces, hyphens, plus signs).
     */
    public String cleanPhoneNumber(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[^0-9]", "");
    }
}
