package com.doppsy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Enterprise service managing bidirectional communication with Meta's WhatsApp Business Cloud API:
 * outbound notifications, media ingestion (voice notes), and document routing (PDF prescriptions).
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
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    /**
     * Sends a plain text message to the specified recipient phone number.
     */
    public boolean sendMessage(String recipientMobile, String messageBody) {
        return sendTextMessage(recipientMobile, messageBody);
    }

    /**
     * Sends a plain text message to the specified recipient phone number.
     */
    public boolean sendTextMessage(String recipientMobile, String messageBody) {

        // Guard clause: validate inputs
        if (recipientMobile == null || recipientMobile.isBlank() || messageBody == null || messageBody.isBlank()) {
            log.warn("[WHATSAPP OUTBOUND] Aborted: recipient mobile or message body is empty");
            return false;
        }

        String sanitizedRecipient = cleanPhoneNumber(recipientMobile);
        log.info("[WHATSAPP OUTBOUND] Dispatching text to: {} (Length: {} chars)", sanitizedRecipient, messageBody.length());

        if (isMockMode()) {
            log.info("[WHATSAPP SIMULATION] Successfully simulated outbound message to: {}", sanitizedRecipient);
            return true;
        }

        try {
            String endpointUrl = String.format("%s/%s/messages", apiUrl, phoneNumberId);

            Map<String, Object> outboundPayload = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", sanitizedRecipient,
                "type", "text",
                "text", Map.of(
                    "preview_url", false,
                    "body", messageBody
                )
            );

            HttpRequest outboundRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(outboundPayload)))
                .build();

            HttpResponse<String> apiResponse = httpClient.send(outboundRequest, HttpResponse.BodyHandlers.ofString());

            if (apiResponse.statusCode() >= 200 && apiResponse.statusCode() < 300) {
                log.info("[WHATSAPP API SUCCESS] Message dispatched to: {}, StatusCode: {}", sanitizedRecipient, apiResponse.statusCode());
                return true;
            } else {
                log.error("[WHATSAPP API ERROR] Failed to dispatch message to: {}, Status: {}, Body: {}",
                    sanitizedRecipient, apiResponse.statusCode(), apiResponse.body());
                return false;
            }
        } catch (Exception dispatchException) {
            log.error("[WHATSAPP EXCEPTION] Error dispatching message to {}: {}", sanitizedRecipient, dispatchException.getMessage(), dispatchException);
            return false;
        }
    }

    /**
     * Downloads an audio or document binary from Meta's media CDN via the WhatsApp Cloud API.
     * Flow:
     * 1. GET https://graph.facebook.com/v20.0/{mediaId} -> returns JSON with CDN download URL
     * 2. GET {cdnUrl} with Bearer token & User-Agent -> returns raw binary bytes
     */
    public byte[] downloadMedia(String metaMediaId) {
        // Guard clause: validate media identifier
        if (metaMediaId == null || metaMediaId.isBlank()) {
            throw new IllegalArgumentException("Meta media_id is required to download media binary");
        }

        log.info("[WHATSAPP MEDIA] Initiating media download for MediaID: {}", metaMediaId);

        if (isMockMode()) {
            log.info("[WHATSAPP SIMULATION] Returning synthetic audio binary for MediaID: {}", metaMediaId);
            return "MOCK_AUDIO_OGG_BINARY_CONTENT_FOR_DOPPSY_VOICE_NOTE".getBytes(StandardCharsets.UTF_8);
        }

        try {
            // Step 1: Query Meta Graph API for the direct CDN URL
            String mediaMetadataUrl = String.format("%s/%s", apiUrl, metaMediaId);
            HttpRequest metadataRequest = HttpRequest.newBuilder()
                .uri(URI.create(mediaMetadataUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

            HttpResponse<String> metadataResponse = httpClient.send(metadataRequest, HttpResponse.BodyHandlers.ofString());
            if (metadataResponse.statusCode() < 200 || metadataResponse.statusCode() >= 300) {
                throw new IOException("Failed to resolve Meta media URL: HTTP " + metadataResponse.statusCode() + " - " + metadataResponse.body());
            }

            JsonNode metadataJson = objectMapper.readTree(metadataResponse.body());
            String cdnDownloadUrl = metadataJson.path("url").asText();

            if (cdnDownloadUrl == null || cdnDownloadUrl.isBlank()) {
                throw new IOException("Meta API response missing 'url' property for MediaID: " + metaMediaId);
            }

            // Step 2: Download raw binary from Meta lookaside CDN (Meta requires User-Agent header)
            HttpRequest binaryDownloadRequest = HttpRequest.newBuilder()
                .uri(URI.create(cdnDownloadUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("User-Agent", "Doppsy-Healthcare-EMR/1.0")
                .GET()
                .build();

            HttpResponse<byte[]> binaryResponse = httpClient.send(binaryDownloadRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (binaryResponse.statusCode() < 200 || binaryResponse.statusCode() >= 300) {
                throw new IOException("Failed to download media binary from CDN: HTTP " + binaryResponse.statusCode());
            }

            log.info("[WHATSAPP MEDIA SUCCESS] Successfully downloaded binary for MediaID: {}, Size: {} bytes",
                metaMediaId, binaryResponse.body().length);
            return binaryResponse.body();

        } catch (Exception mediaDownloadEx) {
            log.error("[WHATSAPP MEDIA ERROR] Media download failed for MediaID: {}: {}", metaMediaId, mediaDownloadEx.getMessage(), mediaDownloadEx);
            throw new RuntimeException("Failed to download WhatsApp media binary: " + mediaDownloadEx.getMessage(), mediaDownloadEx);
        }
    }

    /**
     * Uploads a document (such as a generated Prescription PDF) to Meta Graph API
     * and returns the resulting media_id for message attachment.
     */
    public String uploadDocumentMedia(byte[] documentBytes, String filename, String mimeType) {
        // Guard clauses
        if (documentBytes == null || documentBytes.length == 0) {
            throw new IllegalArgumentException("Document bytes cannot be empty");
        }
        if (filename == null || filename.isBlank()) {
            filename = "Prescription.pdf";
        }
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "application/pdf";
        }

        log.info("[WHATSAPP MEDIA UPLOAD] Uploading document: '{}' ({} bytes) to WhatsApp Cloud API", filename, documentBytes.length);

        if (isMockMode()) {
            String simulatedMediaId = "mock_media_doc_" + UUID.randomUUID().toString().substring(0, 12);
            log.info("[WHATSAPP SIMULATION] Simulated media upload completed. Generated MediaID: {}", simulatedMediaId);
            return simulatedMediaId;
        }

        try {
            String uploadEndpointUrl = String.format("%s/%s/media", apiUrl, phoneNumberId);
            String boundaryMarker = "----DoppsyFormBoundary" + System.currentTimeMillis();

            // Construct multipart/form-data body
            ByteArrayOutputStream multipartBuffer = new ByteArrayOutputStream();
            
            // Part 1: messaging_product = whatsapp
            multipartBuffer.write(("--" + boundaryMarker + "\r\n").getBytes(StandardCharsets.UTF_8));
            multipartBuffer.write("Content-Disposition: form-data; name=\"messaging_product\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            multipartBuffer.write("whatsapp\r\n".getBytes(StandardCharsets.UTF_8));

            // Part 2: type = application/pdf
            multipartBuffer.write(("--" + boundaryMarker + "\r\n").getBytes(StandardCharsets.UTF_8));
            multipartBuffer.write("Content-Disposition: form-data; name=\"type\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            multipartBuffer.write((mimeType + "\r\n").getBytes(StandardCharsets.UTF_8));

            // Part 3: file binary
            multipartBuffer.write(("--" + boundaryMarker + "\r\n").getBytes(StandardCharsets.UTF_8));
            multipartBuffer.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            multipartBuffer.write(("Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            multipartBuffer.write(documentBytes);
            multipartBuffer.write("\r\n".getBytes(StandardCharsets.UTF_8));

            // Final boundary
            multipartBuffer.write(("--" + boundaryMarker + "--\r\n").getBytes(StandardCharsets.UTF_8));

            HttpRequest uploadRequest = HttpRequest.newBuilder()
                .uri(URI.create(uploadEndpointUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + boundaryMarker)
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBuffer.toByteArray()))
                .build();

            HttpResponse<String> uploadResponse = httpClient.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
            if (uploadResponse.statusCode() < 200 || uploadResponse.statusCode() >= 300) {
                throw new IOException("Meta media upload rejected: HTTP " + uploadResponse.statusCode() + " - " + uploadResponse.body());
            }

            JsonNode responseJson = objectMapper.readTree(uploadResponse.body());
            String uploadedMediaId = responseJson.path("id").asText();

            log.info("[WHATSAPP MEDIA UPLOAD SUCCESS] Uploaded filename: {}, MediaID: {}", filename, uploadedMediaId);
            return uploadedMediaId;

        } catch (Exception uploadException) {
            log.error("[WHATSAPP MEDIA UPLOAD ERROR] Failed to upload document '{}': {}", filename, uploadException.getMessage(), uploadException);
            throw new RuntimeException("Failed to upload document to WhatsApp API: " + uploadException.getMessage(), uploadException);
        }
    }

    /**
     * Sends an official PDF prescription document message to a patient.
     */
    public boolean sendDocumentMessage(String recipientMobile, String mediaId, String caption, String filename) {
        // Guard clause: validate parameters
        if (recipientMobile == null || recipientMobile.isBlank() || mediaId == null || mediaId.isBlank()) {
            log.warn("[WHATSAPP DOCUMENT OUTBOUND] Aborted: recipient or mediaId is missing");
            return false;
        }

        String sanitizedRecipient = cleanPhoneNumber(recipientMobile);
        log.info("[WHATSAPP DOCUMENT OUTBOUND] Sending document to: {}, MediaID: {}, Filename: '{}'",
            sanitizedRecipient, mediaId, filename);

        if (isMockMode()) {
            log.info("[WHATSAPP SIMULATION] Successfully simulated sending document '{}' to: {}", filename, sanitizedRecipient);
            return true;
        }

        try {
            String endpointUrl = String.format("%s/%s/messages", apiUrl, phoneNumberId);

            Map<String, Object> documentAttachment = Map.of(
                "id", mediaId,
                "caption", caption != null ? caption : "Prescription from Doppsy Healthcare",
                "filename", filename != null ? filename : "Prescription.pdf"
            );

            Map<String, Object> documentPayload = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", sanitizedRecipient,
                "type", "document",
                "document", documentAttachment
            );

            HttpRequest documentRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(documentPayload)))
                .build();

            HttpResponse<String> apiResponse = httpClient.send(documentRequest, HttpResponse.BodyHandlers.ofString());

            if (apiResponse.statusCode() >= 200 && apiResponse.statusCode() < 300) {
                log.info("[WHATSAPP DOCUMENT SUCCESS] Document delivered to: {}, StatusCode: {}", sanitizedRecipient, apiResponse.statusCode());
                return true;
            } else {
                log.error("[WHATSAPP DOCUMENT ERROR] Delivery failed to: {}, Status: {}, Body: {}",
                    sanitizedRecipient, apiResponse.statusCode(), apiResponse.body());
                return false;
            }
        } catch (Exception documentEx) {
            log.error("[WHATSAPP DOCUMENT EXCEPTION] Failed to dispatch document to {}: {}", sanitizedRecipient, documentEx.getMessage(), documentEx);
            return false;
        }
    }

    public String cleanPhoneNumber(String rawPhoneNumber) {
        if (rawPhoneNumber == null) return "";
        return rawPhoneNumber.replaceAll("[^0-9]", "");
    }

    private boolean isMockMode() {
        return accessToken == null || accessToken.isBlank() || accessToken.startsWith("mock_");
    }
}
