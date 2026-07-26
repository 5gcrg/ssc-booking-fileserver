package com.ssc.booking.exception;

/**
 * A caller-supplied path failed the reject-first sanitization (traversal attempt, absolute
 * path, illegal characters, …). Mapped to 403 PATH_VIOLATION — deliberately not 400, so path
 * probing is indistinguishable in severity from an authorization failure.
 */
public class FilePathViolationException extends RuntimeException {

    public FilePathViolationException(String message) {
        super(message);
    }
}
