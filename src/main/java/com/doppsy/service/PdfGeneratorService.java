package com.doppsy.service;

import com.doppsy.dto.PrescriptionMedicineDto;
import com.doppsy.dto.StructuredPrescriptionDto;
import com.doppsy.entity.Appointment;
import com.doppsy.entity.Provider;
import com.doppsy.entity.User;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Service responsible for rendering digital Prescription documents in PDF format
 * using OpenPDF, adhering to clinical layout standards and Doppsy branding.
 */
@Slf4j
@Service
public class PdfGeneratorService {

    private static final Color BRAND_TEAL = new Color(6, 182, 212); // #06B6D4
    private static final Color BRAND_NAVY = new Color(15, 23, 42);   // #0F172A
    private static final Color TABLE_HEADER_BG = new Color(241, 245, 249); // #F1F5F9
    private static final Color TABLE_BORDER = new Color(226, 232, 240);    // #E2E8F0
    private static final Color ACCENT_ROW_BG = new Color(248, 250, 252);   // #F8FAFC

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm a");

    /**
     * Generates a complete clinical PDF prescription from structured AI extraction
     * and saves it as a temporary file on the host server.
     *
     * @param prescriptionDto structured clinical extraction from voice note
     * @param targetAppointment the associated Doppsy appointment
     * @return {@link File} pointer to the temporary PDF created on disk
     */
    public File generatePrescriptionPdfFile(StructuredPrescriptionDto prescriptionDto, Appointment targetAppointment) {
        // Guard clause: validate inputs
        if (prescriptionDto == null) {
            throw new IllegalArgumentException("Structured prescription data must not be null");
        }
        if (targetAppointment == null) {
            throw new IllegalArgumentException("Target appointment cannot be null for prescription generation");
        }

        byte[] pdfBytes = renderPrescriptionPdf(prescriptionDto, targetAppointment);

        try {
            UUID appointmentId = targetAppointment.getAppointmentId();
            File temporaryPdfFile = File.createTempFile("doppsy_rx_" + appointmentId + "_", ".pdf");
            temporaryPdfFile.deleteOnExit(); // Clean up on JVM termination

            try (FileOutputStream fileOutputStream = new FileOutputStream(temporaryPdfFile)) {
                fileOutputStream.write(pdfBytes);
            }

            log.info("[PDF GENERATOR] Saved temporary prescription PDF to: {} ({} bytes)",
                temporaryPdfFile.getAbsolutePath(), pdfBytes.length);
            return temporaryPdfFile;

        } catch (IOException ioException) {
            log.error("[PDF GENERATOR ERROR] Failed to write temporary prescription PDF to disk: {}", ioException.getMessage(), ioException);
            throw new RuntimeException("Could not persist generated prescription PDF: " + ioException.getMessage(), ioException);
        }
    }

    /**
     * Renders the complete PDF document in memory into a byte array.
     */
    public byte[] renderPrescriptionPdf(StructuredPrescriptionDto prescriptionDto, Appointment targetAppointment) {
        // Guard clauses
        if (prescriptionDto == null || targetAppointment == null) {
            throw new IllegalArgumentException("Prescription data and Appointment are required to render PDF");
        }

        Provider provider = targetAppointment.getProvider();
        User patient = targetAppointment.getUser();

        log.info("[PDF GENERATOR] Rendering PDF prescription for Patient: '{}', Doctor: '{}', AppointmentID: {}",
            patient != null ? patient.getFullName() : prescriptionDto.getPatientName(),
            provider != null ? provider.getFullName() : "Doctor",
            targetAppointment.getAppointmentId());

        Document document = new Document(PageSize.A4, 36, 36, 40, 40);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, outputStream);
            document.open();

            // 1. Clinic & Doctor Header
            renderHeader(document, provider);

            // 2. Teal Accent Divider Line
            LineSeparator separator = new LineSeparator(2f, 100f, BRAND_TEAL, Element.ALIGN_CENTER, -4f);
            document.add(separator);
            document.add(new Paragraph(" ")); // Spacer

            // 3. Patient & Appointment Meta Table
            renderPatientMetaTable(document, patient, prescriptionDto, targetAppointment);

            document.add(new Paragraph(" ")); // Spacer

            // 4. Clinical Complaints & Diagnosis Section
            renderClinicalFindings(document, prescriptionDto);

            document.add(new Paragraph(" ")); // Spacer

            // 5. Tabular Medications (Rx)
            renderMedicationsTable(document, prescriptionDto);

            document.add(new Paragraph(" ")); // Spacer

            // 6. Advice & Diet Instructions
            renderGeneralAdvice(document, prescriptionDto);

            document.add(new Paragraph(" ")); // Spacer

            // 7. Doctor Digital Signature Block & Legal Footer
            renderFooter(document, provider);

            document.close();
            return outputStream.toByteArray();

        } catch (DocumentException docEx) {
            log.error("[PDF GENERATOR ERROR] Document creation error: {}", docEx.getMessage(), docEx);
            throw new RuntimeException("Failed to construct prescription PDF: " + docEx.getMessage(), docEx);
        }
    }

    private void renderHeader(Document document, Provider provider) throws DocumentException {
        Font clinicFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BRAND_NAVY);
        Font doctorFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BRAND_TEAL);
        Font specialtyFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);

        String clinicName = (provider != null && provider.getClinicName() != null)
            ? provider.getClinicName()
            : "DOPPSY HEALTHCARE CLINIC PARTNER";

        String doctorName = (provider != null && provider.getFullName() != null)
            ? "Dr. " + provider.getFullName().replace("Dr. ", "")
            : "Dr. Healthcare Specialist";

        String specialtyText = (provider != null && provider.getSpecialty() != null)
            ? provider.getSpecialty().name() + " Specialist • " + (provider.getLocation() != null ? provider.getLocation() : "Clinic Location")
            : "General Physician • Doppsy Verified Clinic";

        Paragraph clinicPara = new Paragraph(clinicName.toUpperCase(), clinicFont);
        clinicPara.setAlignment(Element.ALIGN_LEFT);
        document.add(clinicPara);

        Paragraph docPara = new Paragraph(doctorName + " | " + specialtyText, specialtyFont);
        docPara.setAlignment(Element.ALIGN_LEFT);
        docPara.setSpacingAfter(6f);
        document.add(docPara);
    }

    private void renderPatientMetaTable(Document document, User patient, StructuredPrescriptionDto prescriptionDto, Appointment appointment) throws DocumentException {
        PdfPTable metaTable = new PdfPTable(4);
        metaTable.setWidthPercentage(100);
        metaTable.setWidths(new float[]{25f, 25f, 25f, 25f});

        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.GRAY);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BRAND_NAVY);

        String patientName = (patient != null && patient.getFullName() != null)
            ? patient.getFullName()
            : prescriptionDto.getPatientName();

        String patientAge = (patient != null && patient.getAge() != null)
            ? patient.getAge() + " Yrs"
            : "Adult";

        String consultationDate = appointment.getAppointmentTimestamp() != null
            ? appointment.getAppointmentTimestamp().format(DATE_FORMATTER)
            : "Today";

        String appointmentToken = "APT-" + appointment.getAppointmentId().toString().substring(0, 8).toUpperCase();

        addMetaCell(metaTable, "PATIENT NAME", patientName, labelFont, valueFont);
        addMetaCell(metaTable, "AGE / GENDER", patientAge, labelFont, valueFont);
        addMetaCell(metaTable, "DATE & TIME", consultationDate, labelFont, valueFont);
        addMetaCell(metaTable, "APPOINTMENT ID", appointmentToken, labelFont, valueFont);

        document.add(metaTable);
    }

    private void addMetaCell(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(TABLE_BORDER);
        cell.setPadding(6f);
        cell.setBackgroundColor(TABLE_HEADER_BG);

        cell.addElement(new Paragraph(label, labelFont));
        cell.addElement(new Paragraph(value, valueFont));
        table.addCell(cell);
    }

    private void renderClinicalFindings(Document document, StructuredPrescriptionDto prescriptionDto) throws DocumentException {
        Font sectionHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BRAND_TEAL);
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BRAND_NAVY);

        Paragraph findingsTitle = new Paragraph("CLINICAL ASSESSMENT & DIAGNOSIS", sectionHeaderFont);
        findingsTitle.setSpacingAfter(4f);
        document.add(findingsTitle);

        PdfPTable findingsTable = new PdfPTable(2);
        findingsTable.setWidthPercentage(100);
        findingsTable.setWidths(new float[]{30f, 70f});

        addFindingsRow(findingsTable, "Chief Complaints / Symptoms:", prescriptionDto.getSymptoms() != null ? prescriptionDto.getSymptoms() : "None reported", bodyFont);
        addFindingsRow(findingsTable, "Provisional Diagnosis:", prescriptionDto.getDiagnosis() != null ? prescriptionDto.getDiagnosis() : "General Consultation", bodyFont);

        document.add(findingsTable);
    }

    private void addFindingsRow(PdfPTable table, String label, String value, Font bodyFont) {
        Font boldLabelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.DARK_GRAY);

        PdfPCell labelCell = new PdfPCell(new Phrase(label, boldLabelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(3f);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, bodyFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(3f);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void renderMedicationsTable(Document document, StructuredPrescriptionDto prescriptionDto) throws DocumentException {
        Font rxSymbolFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BRAND_TEAL);
        Paragraph rxHeading = new Paragraph("℞ MEDICATIONS PRESCRIBED", rxSymbolFont);
        rxHeading.setSpacingAfter(6f);
        document.add(rxHeading);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{6f, 34f, 18f, 22f, 20f});

        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BRAND_NAVY);
        addTableHeaderCell(table, "#", headerFont);
        addTableHeaderCell(table, "Medicine Name & Form", headerFont);
        addTableHeaderCell(table, "Dosage", headerFont);
        addTableHeaderCell(table, "Frequency", headerFont);
        addTableHeaderCell(table, "Instructions", headerFont);

        Font rowFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
        int itemIndex = 1;

        if (prescriptionDto.getMedications() != null && !prescriptionDto.getMedications().isEmpty()) {
            for (PrescriptionMedicineDto med : prescriptionDto.getMedications()) {
                Color rowBg = (itemIndex % 2 == 0) ? ACCENT_ROW_BG : Color.WHITE;

                addTableRowCell(table, String.valueOf(itemIndex++), rowFont, rowBg, Element.ALIGN_CENTER);
                addTableRowCell(table, med.getName(), rowFont, rowBg, Element.ALIGN_LEFT);
                addTableRowCell(table, med.getDosage() != null ? med.getDosage() : "-", rowFont, rowBg, Element.ALIGN_CENTER);
                addTableRowCell(table, med.getFrequency() != null ? med.getFrequency() : "-", rowFont, rowBg, Element.ALIGN_LEFT);
                addTableRowCell(table, med.getInstructions() != null ? med.getInstructions() : "As directed", rowFont, rowBg, Element.ALIGN_LEFT);
            }
        } else {
            PdfPCell emptyCell = new PdfPCell(new Phrase("No specific oral medications prescribed.", rowFont));
            emptyCell.setColspan(5);
            emptyCell.setPadding(8f);
            emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(emptyCell);
        }

        document.add(table);
    }

    private void addTableHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(TABLE_HEADER_BG);
        cell.setBorderColor(TABLE_BORDER);
        cell.setPadding(6f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addTableRowCell(PdfPTable table, String text, Font font, Color bgColor, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bgColor);
        cell.setBorderColor(TABLE_BORDER);
        cell.setPadding(5f);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private void renderGeneralAdvice(Document document, StructuredPrescriptionDto prescriptionDto) throws DocumentException {
        if (prescriptionDto.getGeneralAdvice() == null || prescriptionDto.getGeneralAdvice().isBlank()) {
            return;
        }

        Font sectionHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BRAND_TEAL);
        Font adviceFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);

        Paragraph adviceHeading = new Paragraph("GENERAL ADVICE & INSTRUCTIONS", sectionHeaderFont);
        adviceHeading.setSpacingAfter(4f);
        document.add(adviceHeading);

        Paragraph adviceBody = new Paragraph(prescriptionDto.getGeneralAdvice(), adviceFont);
        adviceBody.setSpacingAfter(8f);
        document.add(adviceBody);
    }

    private void renderFooter(Document document, Provider provider) throws DocumentException {
        Font signFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BRAND_NAVY);
        Font legalFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

        PdfPTable footerTable = new PdfPTable(2);
        footerTable.setWidthPercentage(100);
        footerTable.setWidths(new float[]{60f, 40f});

        // Left: AI EMR generation note
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.addElement(new Paragraph("Generated via Doppsy AI Voice-to-EMR Concierge.", legalFont));
        leftCell.addElement(new Paragraph("This is a digitally verified medical prescription.", legalFont));

        // Right: Doctor Signature block
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        String docName = (provider != null && provider.getFullName() != null)
            ? "Dr. " + provider.getFullName().replace("Dr. ", "")
            : "Doctor's Signature";

        Paragraph signTitle = new Paragraph("Digitally Signed by:", legalFont);
        signTitle.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(signTitle);

        Paragraph namePara = new Paragraph(docName, signFont);
        namePara.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(namePara);

        footerTable.addCell(leftCell);
        footerTable.addCell(rightCell);

        document.add(footerTable);
    }
}
