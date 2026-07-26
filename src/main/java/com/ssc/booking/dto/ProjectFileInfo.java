package com.ssc.booking.dto;

import java.time.Instant;

/** One listed project file. key is always relative to the client's project folder. */
public record ProjectFileInfo(
    String key,
    long size,
    Instant lastModified,
    String etag
) {
}
