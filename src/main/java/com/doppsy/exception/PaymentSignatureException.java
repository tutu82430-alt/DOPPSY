package com.doppsy.exception;

/**
 * Thrown when the Razorpay payment signature verification fails,
 * indicating a potentially tampered or invalid payment callback.
 */
public class PaymentSignatureException extends RuntimeException {

    public PaymentSignatureException(String message) {
        super(message);
    }

    public PaymentSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
