package com.ssc.booking.service;

import com.ssc.booking.config.AppProperties;
import com.ssc.booking.config.MinioProperties;
import com.ssc.booking.dto.PresignedUrlResponse;
import com.ssc.booking.dto.ProjectFileInfo;
import com.ssc.booking.dto.ProjectFileListResponse;
import com.ssc.booking.dto.ProjectFileUploadResponse;
import com.ssc.booking.exception.FilePathViolationException;
import com.ssc.booking.exception.FileStorageException;
import com.ssc.booking.exception.FileValidationException;
import com.ssc.booking.exception.ProjectFileNotFoundException;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Storage for external integration clients, hard-scoped to projects/&lt;project-folder&gt;/ in the
 * dedicated projects bucket. The folder comes exclusively from the resolved registry Client
 * (validated at startup) — never from the request — so a caller cannot escape its own prefix
 * no matter what path it supplies; caller paths are additionally reject-first sanitized.
 * All keys returned to callers are folder-relative.
 */
@Service
public class ProjectFileStorageService {

    private static final Logger log = LoggerFactory.getLogger(ProjectFileStorageService.class);
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final int MAX_OBJECT_KEY_LENGTH = 1024;
    private static final int DEFAULT_MAX_KEYS = 100;
    private static final int MAX_MAX_KEYS = 1000;

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final FileStorageService fileStorageService;

    public ProjectFileStorageService(MinioClient minioClient,
                                     MinioProperties minioProperties,
                                     FileStorageService fileStorageService) {
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
        this.fileStorageService = fileStorageService;
    }

    public ProjectFileUploadResponse upload(AppProperties.Integration.Client client,
                                            String relativePath,
                                            MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileValidationException("A file is required.");
        }
        long maxBytes = minioProperties.getMaxProjectFileSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new FileValidationException(String.format(
                "File size exceeds the %dMB limit for project files.",
                minioProperties.getMaxProjectFileSizeMb()));
        }

        String sanitizedPath = ObjectKeySanitizer.sanitizeRelativePath(relativePath);
        rejectBlockedExtension(sanitizedPath);
        String objectKey = objectKey(client, sanitizedPath);
        String bucket = projectsBucket();

        boolean overwritten = exists(bucket, objectKey);
        // Never trust the client-declared content type for validation — it is stored only so
        // presigned downloads serve a sensible header.
        String contentType = Objects.toString(file.getContentType(), DEFAULT_CONTENT_TYPE);
        try {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(contentType)
                    .build()
            );
        } catch (Exception e) {
            throw new FileStorageException("File upload failed. Please try again.", e);
        }
        log.info("Project file uploaded client={} key={} size={} overwritten={}",
            client.getName(), objectKey, file.getSize(), overwritten);
        return new ProjectFileUploadResponse(sanitizedPath, file.getSize(), contentType, overwritten, Instant.now());
    }

    public PresignedUrlResponse presignedDownloadUrl(AppProperties.Integration.Client client,
                                                     String relativePath) {
        String objectKey = objectKey(client, ObjectKeySanitizer.sanitizeRelativePath(relativePath));
        String bucket = projectsBucket();
        if (!exists(bucket, objectKey)) {
            throw new ProjectFileNotFoundException("File not found.");
        }
        String url = fileStorageService.getPresignedDownloadUrl(objectKey, bucket);
        int expirySeconds = minioProperties.getPresignedUrlExpiry();
        return new PresignedUrlResponse(url, Instant.now().plusSeconds(expirySeconds), expirySeconds);
    }

    public ProjectFileListResponse list(AppProperties.Integration.Client client,
                                        String subPrefix,
                                        Integer maxKeys,
                                        String startAfter) {
        int limit = maxKeys == null || maxKeys <= 0 ? DEFAULT_MAX_KEYS : Math.min(maxKeys, MAX_MAX_KEYS);
        String basePrefix = basePrefix(client);
        String fullPrefix = basePrefix + ObjectKeySanitizer.sanitizePrefix(subPrefix);

        ListObjectsArgs.Builder args = ListObjectsArgs.builder()
            .bucket(projectsBucket())
            .prefix(fullPrefix)
            .recursive(true);
        if (startAfter != null && !startAfter.isBlank()) {
            args.startAfter(basePrefix + ObjectKeySanitizer.sanitizeRelativePath(startAfter));
        }

        // Fetch limit+1 to learn whether more objects follow without a second round-trip.
        List<ProjectFileInfo> files = new ArrayList<>(limit);
        boolean truncated = false;
        try {
            for (Result<Item> result : minioClient.listObjects(args.build())) {
                if (files.size() == limit) {
                    truncated = true;
                    break;
                }
                Item item = result.get();
                files.add(new ProjectFileInfo(
                    item.objectName().substring(basePrefix.length()),
                    item.size(),
                    item.lastModified() != null ? item.lastModified().toInstant() : null,
                    item.etag()
                ));
            }
        } catch (Exception e) {
            throw new FileStorageException("Could not list project files.", e);
        }
        String nextStartAfter = truncated ? files.get(files.size() - 1).key() : null;
        return new ProjectFileListResponse(files, truncated, nextStartAfter);
    }

    /**
     * Idempotent: deleting a missing key still returns normally (MinIO removeObject succeeds),
     * which avoids a stat round-trip and the delete/delete race. Callers always get 204.
     */
    public void delete(AppProperties.Integration.Client client, String relativePath) {
        String objectKey = objectKey(client, ObjectKeySanitizer.sanitizeRelativePath(relativePath));
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(projectsBucket())
                    .object(objectKey)
                    .build()
            );
        } catch (Exception e) {
            throw new FileStorageException("Could not delete project file.", e);
        }
        log.info("Project file deleted client={} key={}", client.getName(), objectKey);
    }

    private String projectsBucket() {
        return minioProperties.getBuckets().getProjects();
    }

    private String basePrefix(AppProperties.Integration.Client client) {
        return "projects/" + client.getProjectFolder() + "/";
    }

    private String objectKey(AppProperties.Integration.Client client, String sanitizedRelativePath) {
        String key = basePrefix(client) + sanitizedRelativePath;
        // Belt-and-braces assertion of the scoping invariant; sanitization should make these
        // unreachable, so treat any failure as a path violation rather than a server error.
        if (!key.startsWith("projects/" + client.getProjectFolder() + "/")
            || key.contains("/../") || key.length() > MAX_OBJECT_KEY_LENGTH) {
            throw new FilePathViolationException("Invalid file path.");
        }
        return key;
    }

    private void rejectBlockedExtension(String sanitizedPath) {
        int dotIndex = sanitizedPath.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == sanitizedPath.length() - 1) {
            return;
        }
        String extension = sanitizedPath.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        List<String> blocked = minioProperties.getBlockedExtensions();
        if (blocked != null && blocked.stream().anyMatch(extension::equalsIgnoreCase)) {
            throw new FileValidationException("Files with extension ." + extension + " are not accepted.");
        }
    }

    private boolean exists(String bucket, String objectKey) {
        try {
            minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            return false;
        } catch (Exception e) {
            throw new FileStorageException("Could not check file existence.", e);
        }
    }
}
