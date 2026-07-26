package com.ssc.booking.dto;

import java.time.Instant;

/** Result of a project-file upload. key is always relative to the client's project folder. */
public record ProjectFileUploadResponse(
    String key,
    long size,
    String contentType,
    boolean overwritten,
    Instant uploadedAt
) {
}
