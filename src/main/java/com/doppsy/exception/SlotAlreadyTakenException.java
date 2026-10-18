package com.doppsy.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a concurrent transaction locks or books a requested consultation slot
 * before the current transaction can acquire it.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class SlotAlreadyTakenException extends RuntimeException {

    public SlotAlreadyTakenException(String message) {
        super(message);
    }
}
