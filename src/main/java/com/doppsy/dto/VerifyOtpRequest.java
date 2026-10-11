package com.doppsy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for the Verify-OTP endpoint.
 * Carries the mobile number and the OTP entered by the user.
 *
 * <p>In the mock flow, any OTP equal to {@code 123456} is accepted.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyOtpRequest {

    @NotBlank(message = "Mobile number is required")
    @Pattern(
        regexp = "^[0-9]{7,15}$",
        message = "Mobile number must contain 7–15 digits"
    )
    private String mobileNumber;

    @NotBlank(message = "OTP is required")
    @Size(min = 4, max = 8, message = "OTP must be between 4 and 8 digits")
    private String otp;
}
