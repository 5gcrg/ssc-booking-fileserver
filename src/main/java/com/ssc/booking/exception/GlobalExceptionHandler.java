package com.ssc.booking.exception;

import com.ssc.booking.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse(
                "UNAUTHORIZED",
                "Authentication required.",
                HttpStatus.UNAUTHORIZED.value()
            ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse(
                "FORBIDDEN",
                "You do not have permission to perform this action.",
                HttpStatus.FORBIDDEN.value()
            ));
    }

    @ExceptionHandler(FilePathViolationException.class)
    public ResponseEntity<ErrorResponse> handleFilePathViolation(FilePathViolationException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse(
                "PATH_VIOLATION",
                "Invalid file path.",
                HttpStatus.FORBIDDEN.value()
            ));
    }

    @ExceptionHandler(ProjectFileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProjectFileNotFound(ProjectFileNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(
                "FILE_NOT_FOUND",
                ex.getMessage(),
                HttpStatus.NOT_FOUND.value()
            ));
    }

    @ExceptionHandler(FileValidationException.class)
    public ResponseEntity<ErrorResponse> handleFileValidation(FileValidationException ex) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse(
                "FILE_VALIDATION_ERROR",
                ex.getMessage(),
                HttpStatus.BAD_REQUEST.value()
            ));
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ErrorResponse> handleFileStorage(FileStorageException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse(
                "FILE_STORAGE_ERROR",
                "File operation failed. Please try again.",
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse(
                "FILE_TOO_LARGE",
                "File size exceeds the allowed limit.",
                HttpStatus.BAD_REQUEST.value()
            ));
    }
}
