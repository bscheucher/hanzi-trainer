package com.learning.hanzitrainer.common;

/** The request is well-formed JSON but not valid for the resource, e.g. an answer of the wrong shape. Mapped to 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
