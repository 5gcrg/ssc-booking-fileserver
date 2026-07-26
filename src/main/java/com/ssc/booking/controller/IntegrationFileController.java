package com.ssc.booking.controller;

import com.ssc.booking.config.AppProperties;
import com.ssc.booking.dto.PresignedUrlResponse;
import com.ssc.booking.dto.ProjectFileListResponse;
import com.ssc.booking.dto.ProjectFileUploadResponse;
import com.ssc.booking.security.IntegrationApiKeyFilter;
import com.ssc.booking.service.ProjectFileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Project-file storage for external integration clients. Authorization is entirely handled by
 * IntegrationApiKeyFilter (X-API-Key) — no @PreAuthorize here; the filter injects the resolved
 * client (with its project folder) as a request attribute. Relative paths always travel as
 * query/form params, never as path variables, so slashes survive routing. All keys in requests
 * and responses are relative to the client's own folder.
 */
@RestController
@RequestMapping("/api/v1/integration/files")
public class IntegrationFileController {

    private final ProjectFileStorageService projectFileStorageService;

    public IntegrationFileController(ProjectFileStorageService projectFileStorageService) {
        this.projectFileStorageService = projectFileStorageService;
    }

    @PostMapping
    public ResponseEntity<ProjectFileUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("path") String path,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(projectFileStorageService.upload(client(request), path, file));
    }

    @GetMapping("/url")
    public ResponseEntity<PresignedUrlResponse> presignedUrl(
            @RequestParam("path") String path,
            HttpServletRequest request) {
        return ResponseEntity.ok(projectFileStorageService.presignedDownloadUrl(client(request), path));
    }

    @GetMapping
    public ResponseEntity<ProjectFileListResponse> list(
            @RequestParam(value = "prefix", required = false) String prefix,
            @RequestParam(value = "maxKeys", required = false) Integer maxKeys,
            @RequestParam(value = "startAfter", required = false) String startAfter,
            HttpServletRequest request) {
        return ResponseEntity.ok(projectFileStorageService.list(client(request), prefix, maxKeys, startAfter));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(
            @RequestParam("path") String path,
            HttpServletRequest request) {
        projectFileStorageService.delete(client(request), path);
        return ResponseEntity.noContent().build();
    }

    private AppProperties.Integration.Client client(HttpServletRequest request) {
        return (AppProperties.Integration.Client) request.getAttribute(IntegrationApiKeyFilter.CLIENT_ATTRIBUTE);
    }
}
