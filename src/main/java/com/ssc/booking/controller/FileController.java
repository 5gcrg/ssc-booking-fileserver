package com.ssc.booking.controller;

import com.ssc.booking.dto.FileUploadResponse;
import com.ssc.booking.dto.PresignedUrlResponse;
import com.ssc.booking.service.FileStorageService;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/download")
    public ResponseEntity<org.springframework.core.io.Resource> downloadFile(
        @RequestParam("bucket") String bucket,
        @RequestParam("objectKey") String objectKey
    ) {
        org.springframework.core.io.Resource resource = fileStorageService.loadAsResource(bucket, objectKey);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        String contentType = bucket.contains("template")
            ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            : "application/pdf";
        String filename = fileStorageService.resolveLocalPath(bucket, objectKey).getFileName().toString();

        return ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
            .body(resource);
    }

    @PostMapping("/documents/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FileUploadResponse> uploadDocument(
        @RequestParam("file") MultipartFile file,
        @RequestParam("submissionId") String submissionId,
        @RequestParam("documentId") String documentId,
        @RequestParam("versionNumber") int versionNumber
    ) {
        String objectKey = fileStorageService.uploadDocument(file, submissionId, documentId, versionNumber);
        return ResponseEntity.ok(new FileUploadResponse(
            objectKey,
            file.getOriginalFilename(),
            file.getSize(),
            Instant.now(),
            "File uploaded successfully."
        ));
    }

    @GetMapping("/documents/{objectKey}/url")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PresignedUrlResponse> getDocumentUrl(@PathVariable String objectKey) {
        return buildDocumentUrlResponse(objectKey);
    }

    @GetMapping("/documents/url")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PresignedUrlResponse> getDocumentUrlByQuery(@RequestParam("objectKey") String objectKey) {
        return buildDocumentUrlResponse(objectKey);
    }

    private ResponseEntity<PresignedUrlResponse> buildDocumentUrlResponse(String objectKey) {
        String presignedUrl = fileStorageService.getPresignedDocumentUrl(objectKey);
        if (presignedUrl.startsWith("/")) {
            String baseUrl = org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
            if (baseUrl.contains(".up.railway.app") && baseUrl.startsWith("http://")) {
                baseUrl = baseUrl.replace("http://", "https://");
            }
            presignedUrl = baseUrl + presignedUrl;
        }
        int expiresInSeconds = fileStorageService.getPresignedUrlExpirySeconds();
        return ResponseEntity.ok(new PresignedUrlResponse(
            presignedUrl,
            Instant.now().plusSeconds(expiresInSeconds),
            expiresInSeconds
        ));
    }

    @PostMapping("/templates/upload")
    @PreAuthorize("hasAnyRole('SSC_ADMIN', 'SSC_SYSTEM_ADMIN')")
    public ResponseEntity<FileUploadResponse> uploadTemplate(
        @RequestParam("file") MultipartFile file,
        @RequestParam("templateType") String templateType,
        @RequestParam("versionNumber") int versionNumber
    ) {
        String objectKey = fileStorageService.uploadTemplate(file, templateType, versionNumber);
        return ResponseEntity.ok(new FileUploadResponse(
            objectKey,
            file.getOriginalFilename(),
            file.getSize(),
            Instant.now(),
            "File uploaded successfully."
        ));
    }

    @GetMapping("/templates/{objectKey}/url")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PresignedUrlResponse> getTemplateUrl(@PathVariable String objectKey) {
        return buildTemplateUrlResponse(objectKey);
    }

    @GetMapping("/templates/url")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PresignedUrlResponse> getTemplateUrlByQuery(@RequestParam("objectKey") String objectKey) {
        return buildTemplateUrlResponse(objectKey);
    }

    private ResponseEntity<PresignedUrlResponse> buildTemplateUrlResponse(String objectKey) {
        String presignedUrl = fileStorageService.getPresignedTemplateUrl(objectKey);
        if (presignedUrl.startsWith("/")) {
            String baseUrl = org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
            if (baseUrl.contains(".up.railway.app") && baseUrl.startsWith("http://")) {
                baseUrl = baseUrl.replace("http://", "https://");
            }
            presignedUrl = baseUrl + presignedUrl;
        }
        int expiresInSeconds = fileStorageService.getPresignedUrlExpirySeconds();
        return ResponseEntity.ok(new PresignedUrlResponse(
            presignedUrl,
            Instant.now().plusSeconds(expiresInSeconds),
            expiresInSeconds
        ));
    }
}
