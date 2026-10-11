package com.doppsy.exception;

/**
 * Thrown when an authenticated user attempts to access or modify a resource
 * that belongs to another entity (e.g. updating an appointment assigned to another provider).
 * Results in HTTP 403 Forbidden.
 */
public class UnauthorizedAccessException extends RuntimeException {

    public UnauthorizedAccessException(String message) {
        super(message);
    }
}
