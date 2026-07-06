package com.ssc.booking.service;

import com.ssc.booking.config.MinioProperties;
import com.ssc.booking.exception.FileStorageException;
import com.ssc.booking.exception.FileValidationException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String DOCX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final int MAX_FILENAME_LENGTH = 200;

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public FileStorageService(MinioClient minioClient, MinioProperties minioProperties) {
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
    }

    public String uploadDocument(
        MultipartFile file,
        String submissionId,
        String documentId,
        int versionNumber
    ) {
        validatePresent(file);
        validateExtension(file, ".pdf", "Only PDF files are accepted.");
        if (!PDF_CONTENT_TYPE.equalsIgnoreCase(file.getContentType())) {
            throw new FileValidationException("Only PDF files are accepted.");
        }
        validateMaxSize(file);

        String objectKey = String.format(
            "submissions/%s/%s/v%d/%s",
            sanitizeObjectSegment(submissionId),
            sanitizeObjectSegment(documentId),
            versionNumber,
            sanitizeFilename(file.getOriginalFilename())
        );

        upload(file, minioProperties.getBuckets().getDocuments(), objectKey, PDF_CONTENT_TYPE);
        return objectKey;
    }

    public String uploadTemplate(
        MultipartFile file,
        String templateType,
        int versionNumber
    ) {
        validatePresent(file);
        validateExtension(file, ".docx", "Only .docx files are accepted for templates.");
        validateMaxSize(file);

        String objectKey = String.format(
            "templates/%s/v%d/%s",
            sanitizeObjectSegment(templateType),
            versionNumber,
            sanitizeFilename(file.getOriginalFilename())
        );

        upload(file, minioProperties.getBuckets().getTemplates(), objectKey, DOCX_CONTENT_TYPE);
        return objectKey;
    }

    public String getPresignedDownloadUrl(String objectKey, String bucket) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(minioProperties.getPresignedUrlExpiry(), TimeUnit.SECONDS)
                    .build()
            );
        } catch (Exception e) {
            throw new FileStorageException("Could not generate file download URL.", e);
        }
    }

    public String getPresignedDocumentUrl(String objectKey) {
        return getPresignedDownloadUrl(objectKey, minioProperties.getBuckets().getDocuments());
    }

    public String getPresignedTemplateUrl(String objectKey) {
        return getPresignedDownloadUrl(objectKey, minioProperties.getBuckets().getTemplates());
    }

    public void deleteDocument(String objectKey) {
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBuckets().getDocuments())
                    .object(objectKey)
                    .build()
            );
            // Only called by Super Admin for GDPR/data retention. Not exposed via student API.
            log.info("Deleted document objectKey={} at {}", objectKey, Instant.now());
        } catch (Exception e) {
            throw new FileStorageException("Could not delete document.", e);
        }
    }

    public boolean fileExists(String bucket, String objectKey) {
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

    public int getPresignedUrlExpirySeconds() {
        return minioProperties.getPresignedUrlExpiry();
    }

    private void upload(MultipartFile file, String bucket, String objectKey, String contentType) {
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
    }

    private void validatePresent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileValidationException("A file is required.");
        }
    }

    private void validateExtension(MultipartFile file, String extension, String message) {
        String filename = Objects.toString(file.getOriginalFilename(), "");
        if (!filename.toLowerCase().endsWith(extension)) {
            throw new FileValidationException(message);
        }
    }

    private void validateMaxSize(MultipartFile file) {
        long maxBytes = minioProperties.getMaxFileSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new FileValidationException(
                String.format("File size exceeds the %dMB limit.", minioProperties.getMaxFileSizeMb())
            );
        }
    }

    private String sanitizeFilename(String filename) {
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

    private String sanitizeObjectSegment(String segment) {
        String cleaned = Objects.toString(segment, "")
            .trim()
            .replaceAll("\\s+", "_")
            .replaceAll("[^A-Za-z0-9._-]", "_");
        if (cleaned.isBlank()) {
            throw new FileValidationException("Storage path values must not be blank.");
        }
        return cleaned;
    }
}
