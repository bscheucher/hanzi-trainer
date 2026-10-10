package com.learning.hanzitrainer.common;

/** A requested resource does not exist. Mapped to 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Object id) {
        super(resource + " " + id + " not found");
    }
}
