package com.doppsy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Request body for the Send-OTP endpoint.
 * Validates that the mobile number is in a broadly acceptable format
 * (digits only, 7–15 chars — covers E.164 without the leading '+').
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendOtpRequest {

    @NotBlank(message = "Mobile number is required")
    @Pattern(
        regexp = "^[0-9]{7,15}$",
        message = "Mobile number must contain 7–15 digits"
    )
    private String mobileNumber;
}
