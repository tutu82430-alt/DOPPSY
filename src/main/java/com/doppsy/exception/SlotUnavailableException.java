package com.doppsy.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a patient tries to book a time slot that is already taken
 * or is not in {@code AVAILABLE} status.
 *
 * <p>Maps to HTTP 409 Conflict — the request was valid but conflicts with
 * the current state of the resource.</p>
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class SlotUnavailableException extends RuntimeException {

    public SlotUnavailableException(String message) {
        super(message);
    }
}
