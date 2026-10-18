package com.doppsy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for verifying a Razorpay payment after the client
 * completes the checkout flow.
 *
 * <p>All three fields are mandatory and are provided by the Razorpay
 * checkout callback on success.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPaymentRequest {

    /** The Razorpay order ID (e.g. {@code order_Abc123XyZ}). */
    @NotBlank(message = "Razorpay Order ID is required")
    private String razorpayOrderId;

    /** The Razorpay payment ID (e.g. {@code pay_Abc123XyZ}). */
    @NotBlank(message = "Razorpay Payment ID is required")
    private String razorpayPaymentId;

    /** The HMAC SHA256 signature for tamper-proof verification. */
    @NotBlank(message = "Razorpay Signature is required")
    private String razorpaySignature;
}
