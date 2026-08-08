package com.mediconnect.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an invalid operation is attempted on an appointment (e.g. rescheduling a cancelled appointment).
 * Maps to HTTP 400 Bad Request.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidAppointmentOperationException extends RuntimeException {

    public InvalidAppointmentOperationException(String message) {
        super(message);
    }
}
