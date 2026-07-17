package com.ssc.booking.dto;

import java.time.Instant;

public record PresignedUrlResponse(
    String presignedUrl,
    Instant expiresAt,
    int expiresInSeconds
) {
}
