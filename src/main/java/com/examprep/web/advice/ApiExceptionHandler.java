package com.examprep.web.advice;

import com.examprep.ai.exception.AiConfigurationException;
import com.examprep.ai.exception.AiGenerationException;
import com.examprep.ai.exception.AiUnavailableException;
import com.examprep.common.ResourceNotFoundException;
import com.examprep.common.ValidationException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * JSON errors for asynchronous browser interactions. Messages are safe to display and never
 * contain document contents or configuration secrets.
 */
@RestControllerAdvice(basePackages = "com.examprep.web.api")
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(ResourceNotFoundException notFound) {
        return error(HttpStatus.NOT_FOUND, notFound.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, String>> invalid(ValidationException invalid) {
        return error(HttpStatus.BAD_REQUEST, invalid.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> denied(AccessDeniedException denied) {
        return error(HttpStatus.FORBIDDEN, "You do not have access to this material.");
    }

    @ExceptionHandler(AiConfigurationException.class)
    public ResponseEntity<Map<String, String>> aiNotConfigured(AiConfigurationException configuration) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, configuration.getMessage());
    }

    @ExceptionHandler(AiUnavailableException.class)
    public ResponseEntity<Map<String, String>> aiUnavailable(AiUnavailableException unavailable) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, unavailable.getMessage());
    }

    @ExceptionHandler(AiGenerationException.class)
    public ResponseEntity<Map<String, String>> aiInvalid(AiGenerationException invalid) {
        return error(HttpStatus.BAD_GATEWAY,
                "The AI response could not be validated. Please try again.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalidBody(MethodArgumentNotValidException invalid) {
        String message = invalid.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("Please check the submitted values.");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception failure) {
        log.error("Unhandled API error", failure);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "The request could not be completed.");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message",
                message == null ? status.getReasonPhrase() : message));
    }
}
