package com.ssc.booking.dto;

import java.time.Instant;

public record FileUploadResponse(
    String objectKey,
    String fileName,
    long fileSizeBytes,
    Instant uploadedAt,
    String message
) {
}
