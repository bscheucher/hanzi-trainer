package com.learning.hanzitrainer.common;

/** A chat model's reply had no text or could not be read as the requested structure, e.g. truncated JSON. */
public class UnreadableReplyException extends RuntimeException {

    public UnreadableReplyException(String message) {
        super(message);
    }

    public UnreadableReplyException(String message, Throwable cause) {
        super(message, cause);
    }
}
