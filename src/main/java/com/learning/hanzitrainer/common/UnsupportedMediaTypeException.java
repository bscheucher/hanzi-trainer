package com.learning.hanzitrainer.common;

/** Uploaded content is of a type the endpoint does not accept. Mapped to 415. */
public class UnsupportedMediaTypeException extends RuntimeException {

    public UnsupportedMediaTypeException(String message) {
        super(message);
    }
}
