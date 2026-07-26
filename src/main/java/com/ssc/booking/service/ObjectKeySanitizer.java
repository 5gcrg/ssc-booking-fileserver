package com.ssc.booking.service;

import com.ssc.booking.exception.FilePathViolationException;
import com.ssc.booking.exception.FileValidationException;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * Object-key sanitization shared by the JWT-gated document storage and the API-key-gated
 * project storage. sanitizeFilename/sanitizeObjectSegment keep their historical replace-based
 * behavior (extracted verbatim from FileStorageService). sanitizeRelativePath is reject-first:
 * anything that even looks like traversal or an absolute path throws FilePathViolationException
 * (→ 403) instead of being silently repaired.
 */
public final class ObjectKeySanitizer {

    static final int MAX_FILENAME_LENGTH = 200;
    private static final int MAX_RELATIVE_PATH_LENGTH = 512;
    private static final int MAX_SEGMENTS = 20;

    private ObjectKeySanitizer() {
    }

    public static String sanitizeFilename(String filename) {
        String cleaned = StringUtils.cleanPath(Objects.toString(filename, "file"));
        cleaned = cleaned.replace("..", "")
            .replaceAll("[/\\\\]+", "")
            .replaceAll("\\s+", "_")
            .replaceAll("[^A-Za-z0-9._-]", "_");

        if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("_")) {
            cleaned = "file";
        }

        if (cleaned.length() <= MAX_FILENAME_LENGTH) {
            return cleaned;
        }

        int extensionIndex = cleaned.lastIndexOf('.');
        if (extensionIndex > 0) {
            String extension = cleaned.substring(extensionIndex);
            int baseLength = Math.max(1, MAX_FILENAME_LENGTH - extension.length());
            return cleaned.substring(0, Math.min(baseLength, extensionIndex)) + extension;
        }
        return cleaned.substring(0, MAX_FILENAME_LENGTH);
    }

    public static String sanitizeObjectSegment(String segment) {
        String cleaned = Objects.toString(segment, "")
            .trim()
            .replaceAll("\\s+", "_")
            .replaceAll("[^A-Za-z0-9._-]", "_");
        if (cleaned.isBlank()) {
            throw new FileValidationException("Storage path values must not be blank.");
        }
        return cleaned;
    }

    /**
     * Sanitize a caller-supplied folder-relative path like "builds/v1.2/app.zip". Intermediate
     * segments go through sanitizeObjectSegment, the last through sanitizeFilename.
     */
    public static String sanitizeRelativePath(String relativePath) {
        String[] segments = splitValidated(relativePath);
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            boolean last = i == segments.length - 1;
            String sanitized = last ? sanitizeFilename(segments[i]) : sanitizeObjectSegment(segments[i]);
            requireSafeSegment(sanitized);
            if (i > 0) {
                joined.append('/');
            }
            joined.append(sanitized);
        }
        return joined.toString();
    }

    /**
     * Sanitize a caller-supplied list prefix. Unlike sanitizeRelativePath, a trailing slash is
     * meaningful ("builds/" must not match "builds-old/") and is preserved; blank means "whole
     * folder". Every segment goes through sanitizeObjectSegment — a prefix's last segment may be
     * a partial name, not a filename.
     */
    public static String sanitizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        boolean trailingSlash = prefix.endsWith("/");
        String trimmed = trailingSlash ? prefix.substring(0, prefix.length() - 1) : prefix;
        String[] segments = splitValidated(trimmed);
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            String sanitized = sanitizeObjectSegment(segments[i]);
            requireSafeSegment(sanitized);
            if (i > 0) {
                joined.append('/');
            }
            joined.append(sanitized);
        }
        if (trailingSlash) {
            joined.append('/');
        }
        return joined.toString();
    }

    private static String[] splitValidated(String path) {
        if (path == null || path.isBlank()) {
            throw new FilePathViolationException("Path must not be blank.");
        }
        if (path.length() > MAX_RELATIVE_PATH_LENGTH) {
            throw new FilePathViolationException("Path exceeds " + MAX_RELATIVE_PATH_LENGTH + " characters.");
        }
        if (path.contains("\\")) {
            throw new FilePathViolationException("Backslashes are not allowed in paths.");
        }
        if (path.startsWith("/") || path.contains("//") || path.endsWith("/")) {
            throw new FilePathViolationException("Paths must be relative with single slash separators.");
        }
        // '%' is rejected outright so an already-decoded traversal sequence can never be smuggled
        // through a second decode downstream (policy choice, not a correctness requirement).
        if (path.contains("%")) {
            throw new FilePathViolationException("Percent characters are not allowed in paths.");
        }
        for (int i = 0; i < path.length(); i++) {
            if (Character.isISOControl(path.charAt(i))) {
                throw new FilePathViolationException("Control characters are not allowed in paths.");
            }
        }
        String[] segments = path.split("/", -1);
        if (segments.length > MAX_SEGMENTS) {
            throw new FilePathViolationException("Path exceeds " + MAX_SEGMENTS + " segments.");
        }
        for (String segment : segments) {
            requireSafeSegment(segment);
        }
        return segments;
    }

    private static void requireSafeSegment(String segment) {
        if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
            throw new FilePathViolationException("Path segments must not be blank, '.' or '..'.");
        }
    }
}
