package com.doppsy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructuredPrescriptionDto {
    private String patientName;
    private String symptoms;
    private String diagnosis;
    
    @Builder.Default
    private List<PrescriptionMedicineDto> medications = new ArrayList<>();
    
    private String generalAdvice;
}
