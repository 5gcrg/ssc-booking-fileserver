package com.ssc.booking.exception;

/** Requested project file does not exist (within the caller's own folder). Mapped to 404. */
public class ProjectFileNotFoundException extends RuntimeException {

    public ProjectFileNotFoundException(String message) {
        super(message);
    }
}
