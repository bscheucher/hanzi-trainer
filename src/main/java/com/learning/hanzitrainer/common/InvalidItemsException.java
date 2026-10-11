package com.learning.hanzitrainer.common;

import java.util.List;

/** A request with several items failed validation; mapped to 400 with an {@code errors} array. */
public class InvalidItemsException extends RuntimeException {

    private final List<ItemError> errors;

    public InvalidItemsException(String message, List<ItemError> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public List<ItemError> errors() {
        return errors;
    }

    /** @param index position of the item in the request */
    public record ItemError(int index, String message) {
    }
}
