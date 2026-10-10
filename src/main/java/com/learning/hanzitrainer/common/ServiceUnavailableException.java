package com.learning.hanzitrainer.common;

/** A required local resource or external service is not configured or not reachable. Mapped to 503. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
