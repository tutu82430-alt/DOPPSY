package com.doppsy.exception;

import jakarta.persistence.LockTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception handler — converts all Doppsy exceptions into
 * RFC 7807 {@link ProblemDetail} responses so clients receive a consistent
 * JSON error envelope on every error path.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 404 — resource not found */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://doppsy.com/errors/not-found"));
        pd.setTitle("Resource Not Found");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /** 401 — invalid OTP */
    @ExceptionHandler(InvalidOtpException.class)
    public ProblemDetail handleInvalidOtp(InvalidOtpException ex) {
        log.warn("OTP validation failed: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        pd.setType(URI.create("https://doppsy.com/errors/invalid-otp"));
        pd.setTitle("OTP Verification Failed");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /** 409 — slot already booked or unavailable */
    @ExceptionHandler(SlotUnavailableException.class)
    public ProblemDetail handleSlotUnavailable(SlotUnavailableException ex) {
        log.warn("Slot unavailable: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("https://doppsy.com/errors/slot-unavailable"));
        pd.setTitle("Slot Unavailable");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /** 503 — DB row lock timeout (too many concurrent booking attempts) */
    @ExceptionHandler(LockTimeoutException.class)
    public ProblemDetail handleLockTimeout(LockTimeoutException ex) {
        log.warn("Lock timeout during booking: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "The slot is currently being processed. Please retry in a moment.");
        pd.setType(URI.create("https://doppsy.com/errors/lock-timeout"));
        pd.setTitle("Booking Lock Timeout");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /** 400 — @Valid bean validation failures */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                fe -> fe.getDefaultMessage() == null ? "Invalid value" : fe.getDefaultMessage(),
                (a, b) -> a
            ));

        log.warn("Validation failed: {}", fieldErrors);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Request validation failed");
        pd.setType(URI.create("https://doppsy.com/errors/validation"));
        pd.setTitle("Validation Error");
        pd.setProperty("fieldErrors", fieldErrors);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /** 500 — catch-all */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        pd.setType(URI.create("https://doppsy.com/errors/internal"));
        pd.setTitle("Internal Server Error");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
