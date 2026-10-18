package com.doppsy.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when communication with Razorpay API fails during order creation.
 */
@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class RazorpayOrderCreationException extends RuntimeException {

    public RazorpayOrderCreationException(String message, Throwable cause) {
        super(message, cause);
    }

    public RazorpayOrderCreationException(String message) {
        super(message);
    }
}
