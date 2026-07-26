package com.ssc.booking.dto;

import java.util.List;

/** Paginated project-file listing. Pass nextStartAfter back as startAfter to continue. */
public record ProjectFileListResponse(
    List<ProjectFileInfo> files,
    boolean truncated,
    String nextStartAfter
) {
}
