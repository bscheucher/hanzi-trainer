package com.learning.hanzitrainer.common;

/** The request conflicts with the current state, e.g. a duplicate key. Mapped to 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
