package com.doppsy.service;

import com.doppsy.dto.PrescriptionMedicineDto;
import com.doppsy.dto.StructuredPrescriptionDto;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Core clinical AI service responsible for transcribing raw doctor voice notes (via Whisper)
 * and extracting structured clinical entities (via GPT-4o-mini) for zero-click EMR generation.
 */
@Slf4j
@Service
public class VoicePrescriptionService {

    private final String openAiApiKey;
    private final String openAiApiUrl;
    private final String whisperModel;
    private final String chatModel;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public VoicePrescriptionService(
        @Value("${doppsy.openai.api-key:mock_openai_api_key}") String openAiApiKey,
        @Value("${doppsy.openai.api-url:https://api.openai.com/v1}") String openAiApiUrl,
        @Value("${doppsy.openai.whisper-model:whisper-1}") String whisperModel,
        @Value("${doppsy.openai.chat-model:gpt-4o-mini}") String chatModel,
        ObjectMapper objectMapper
    ) {
        this.openAiApiKey = openAiApiKey;
        this.openAiApiUrl = openAiApiUrl;
        this.whisperModel = whisperModel;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    }

    /**
     * Executes the end-to-end AI pipeline: audio bytes -> Whisper raw transcript -> GPT-4o-mini structured EMR.
     *
     * @param audioBytes raw voice note bytes (OGG / MP3 / WAV)
     * @return {@link StructuredPrescriptionDto} containing clinical diagnosis, symptoms, medications, and advice
     */
    public StructuredPrescriptionDto processVoiceNote(byte[] audioBytes) {
        // Guard clause: validate audio binary
        if (audioBytes == null || audioBytes.length == 0) {
            throw new IllegalArgumentException("Voice note audio bytes must not be empty");
        }

        log.info("[VOICE-EMR] Ingestion started. Audio payload size: {} bytes", audioBytes.length);

        // Step 1: Whisper Audio Transcription
        String rawTranscriptText = transcribeAudio(audioBytes);
        log.info("[VOICE-EMR] Whisper transcription completed ({} characters)", rawTranscriptText.length());

        // Step 2: LLM Clinical Structuring (GPT-4o-mini)
        StructuredPrescriptionDto structuredPrescription = structureClinicalTranscript(rawTranscriptText);
        log.info("[VOICE-EMR] Clinical structuring successful for Patient: '{}', Diagnosis: '{}', Medications: {}",
            structuredPrescription.getPatientName(),
            structuredPrescription.getDiagnosis(),
            structuredPrescription.getMedications().size());

        return structuredPrescription;
    }

    /**
     * Step 1: Dispatches audio binary to OpenAI Whisper API (https://api.openai.com/v1/audio/transcriptions).
     */
    public String transcribeAudio(byte[] audioBytes) {
        if (isMockMode()) {
            log.info("[VOICE-EMR-SIMULATION] Using clinical benchmark transcript for mock OpenAI key");
            return "Patient Ramesh Kumar, 38 years old male. Complaining of high grade fever, sore throat, and persistent dry cough for the past 3 days. On physical examination, posterior pharynx shows mild erythema without tonsillar exudates. Chest auscultation clear. Clinical diagnosis is acute upper respiratory tract infection. Prescription: Paracetamol 650mg 1 tablet thrice daily after meals for 3 days. Azithromycin 500mg 1 tablet once daily before lunch for 3 days. Levocetirizine 5mg 1 tablet once daily at bedtime for 5 days. General advice: Maintain adequate hydration with warm fluids, perform steam inhalation twice daily, and schedule a review if temperature persists beyond 72 hours.";
        }

        try {
            String transcriptionEndpoint = openAiApiUrl + "/audio/transcriptions";
            String boundaryMarker = "----WhisperBoundary" + System.currentTimeMillis();

            ByteArrayOutputStream multipartPayload = new ByteArrayOutputStream();

            // Field 1: model = whisper-1
            multipartPayload.write(("--" + boundaryMarker + "\r\n").getBytes(StandardCharsets.UTF_8));
            multipartPayload.write("Content-Disposition: form-data; name=\"model\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            multipartPayload.write((whisperModel + "\r\n").getBytes(StandardCharsets.UTF_8));

            // Field 2: language = en (optional hint for medical vocabulary)
            multipartPayload.write(("--" + boundaryMarker + "\r\n").getBytes(StandardCharsets.UTF_8));
            multipartPayload.write("Content-Disposition: form-data; name=\"language\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            multipartPayload.write("en\r\n".getBytes(StandardCharsets.UTF_8));

            // Field 3: file binary
            multipartPayload.write(("--" + boundaryMarker + "\r\n").getBytes(StandardCharsets.UTF_8));
            multipartPayload.write("Content-Disposition: form-data; name=\"file\"; filename=\"doctor_voice_note.ogg\"\r\n".getBytes(StandardCharsets.UTF_8));
            multipartPayload.write("Content-Type: audio/ogg\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            multipartPayload.write(audioBytes);
            multipartPayload.write("\r\n".getBytes(StandardCharsets.UTF_8));

            // Terminating boundary
            multipartPayload.write(("--" + boundaryMarker + "--\r\n").getBytes(StandardCharsets.UTF_8));

            HttpRequest transcriptionRequest = HttpRequest.newBuilder()
                .uri(URI.create(transcriptionEndpoint))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + openAiApiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundaryMarker)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartPayload.toByteArray()))
                .build();

            HttpResponse<String> transcriptionResponse = httpClient.send(transcriptionRequest, HttpResponse.BodyHandlers.ofString());
            if (transcriptionResponse.statusCode() < 200 || transcriptionResponse.statusCode() >= 300) {
                throw new IOException("Whisper API rejected transcription request: HTTP " + transcriptionResponse.statusCode() + " - " + transcriptionResponse.body());
            }

            JsonNode responseJson = objectMapper.readTree(transcriptionResponse.body());
            String extractedText = responseJson.path("text").asText();

            if (extractedText == null || extractedText.isBlank()) {
                throw new IllegalStateException("Whisper returned an empty transcription for the voice note");
            }

            return extractedText;

        } catch (Exception whisperEx) {
            log.error("[VOICE-EMR ERROR] Whisper transcription failed: {}", whisperEx.getMessage(), whisperEx);
            throw new RuntimeException("Whisper speech-to-text transcription error: " + whisperEx.getMessage(), whisperEx);
        }
    }

    /**
     * Step 2: Submits raw medical transcript to GPT-4o-mini chat completions endpoint
     * with a strict schema prompt to extract a typed JSON object.
     */
    public StructuredPrescriptionDto structureClinicalTranscript(String rawTranscript) {
        // Guard clause
        if (rawTranscript == null || rawTranscript.isBlank()) {
            throw new IllegalArgumentException("Raw transcript text must not be empty");
        }

        if (isMockMode()) {
            return buildSimulatedStructuredPrescription();
        }

        try {
            String completionsEndpoint = openAiApiUrl + "/chat/completions";

            String systemPrompt = """
                You are a senior clinical pharmacologist and medical scribe for the Doppsy Healthcare platform.
                Your task is to parse unstructured doctor voice notes into a rigorous, valid JSON prescription object.

                Extract exactly these fields:
                - patientName: string (patient display name mentioned by doctor, or "Patient" if unspecified)
                - symptoms: string (chief complaints and duration)
                - diagnosis: string (clinical diagnosis or provisional assessment)
                - medications: array of objects, where each object contains:
                    - name: string (generic or trade name of medicine)
                    - dosage: string (e.g. "650mg", "500mg", "5ml")
                    - frequency: string (e.g. "1-0-1 (Twice daily)", "Once daily at bedtime", "Thrice daily after food")
                    - instructions: string (e.g. "Take after food for 3 days", "Before meals")
                - generalAdvice: string (dietary advice, lifestyle changes, hydration, warnings, follow-up instructions)

                Output ONLY pure JSON. Do not include markdown code block formatting.
                """;

            Map<String, Object> requestPayload = Map.of(
                "model", chatModel,
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.1, // low temperature for deterministic clinical precision
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", "Doctor's Voice Transcription:\n" + rawTranscript)
                )
            );

            HttpRequest structuringRequest = HttpRequest.newBuilder()
                .uri(URI.create(completionsEndpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + openAiApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestPayload)))
                .build();

            HttpResponse<String> structuringResponse = httpClient.send(structuringRequest, HttpResponse.BodyHandlers.ofString());
            if (structuringResponse.statusCode() < 200 || structuringResponse.statusCode() >= 300) {
                throw new IOException("OpenAI Chat Completions failed: HTTP " + structuringResponse.statusCode() + " - " + structuringResponse.body());
            }

            JsonNode responseRoot = objectMapper.readTree(structuringResponse.body());
            String rawJsonContent = responseRoot.path("choices").path(0).path("message").path("content").asText();

            // Parse clean JSON into StructuredPrescriptionDto
            JsonNode clinicalJson = objectMapper.readTree(rawJsonContent);

            StructuredPrescriptionDto prescriptionDto = new StructuredPrescriptionDto();
            prescriptionDto.setPatientName(clinicalJson.path("patientName").asText("Patient"));
            prescriptionDto.setSymptoms(clinicalJson.path("symptoms").asText("General malaise / consultation"));
            prescriptionDto.setDiagnosis(clinicalJson.path("diagnosis").asText("Clinical assessment recorded"));
            prescriptionDto.setGeneralAdvice(clinicalJson.path("generalAdvice").asText("Follow prescribed regimen and rest."));

            List<PrescriptionMedicineDto> medicinesList = new ArrayList<>();
            JsonNode medicationsArray = clinicalJson.path("medications");
            if (medicationsArray.isArray()) {
                for (JsonNode medNode : medicationsArray) {
                    medicinesList.add(PrescriptionMedicineDto.builder()
                        .name(medNode.path("name").asText("Standard Medication"))
                        .dosage(medNode.path("dosage").asText("As advised"))
                        .frequency(medNode.path("frequency").asText("As directed"))
                        .instructions(medNode.path("instructions").asText("After meals"))
                        .build());
                }
            }
            prescriptionDto.setMedications(medicinesList);

            return prescriptionDto;

        } catch (Exception llmEx) {
            log.error("[VOICE-EMR ERROR] Failed to structure clinical transcript with LLM: {}", llmEx.getMessage(), llmEx);
            throw new RuntimeException("LLM clinical structuring error: " + llmEx.getMessage(), llmEx);
        }
    }

    private StructuredPrescriptionDto buildSimulatedStructuredPrescription() {
        return StructuredPrescriptionDto.builder()
            .patientName("Ramesh Kumar")
            .symptoms("High grade fever, sore throat, dry cough (3 days duration)")
            .diagnosis("Acute Upper Respiratory Tract Infection (URTI)")
            .generalAdvice("Maintain warm fluid hydration, steam inhalation twice daily, review in 72 hours if fever persists.")
            .medications(List.of(
                PrescriptionMedicineDto.builder()
                    .name("Tab. Paracetamol (Dolo)")
                    .dosage("650 mg")
                    .frequency("1-1-1 (Thrice daily)")
                    .instructions("After meals for 3 days")
                    .build(),
                PrescriptionMedicineDto.builder()
                    .name("Tab. Azithromycin (Azithral)")
                    .dosage("500 mg")
                    .frequency("1-0-0 (Once daily)")
                    .instructions("Before lunch for 3 days")
                    .build(),
                PrescriptionMedicineDto.builder()
                    .name("Tab. Levocetirizine")
                    .dosage("5 mg")
                    .frequency("0-0-1 (Once daily at night)")
                    .instructions("After food for 5 days")
                    .build()
            ))
            .build();
    }

    private boolean isMockMode() {
        return openAiApiKey == null || openAiApiKey.isBlank() || openAiApiKey.startsWith("mock_");
    }
}
