package com.doppsy.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when Razorpay HMAC SHA256 payment signature verification fails,
 * indicating a potentially tampered callback or corrupted transit data.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PaymentSignatureMismatchException extends RuntimeException {

    private final String orderId;

    public PaymentSignatureMismatchException(String orderId, String message) {
        super(message);
        this.orderId = orderId;
    }

    public PaymentSignatureMismatchException(String message) {
        super(message);
        this.orderId = null;
    }

    public String getOrderId() {
        return orderId;
    }
}
