package com.examprep.web.advice;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

import com.examprep.ai.exception.AiRateLimitException;
import com.examprep.ai.exception.AiResponseException;
import com.examprep.ai.exception.AiServiceException;
import com.examprep.ai.exception.AiUnavailableException;
import com.examprep.common.AppException;
import com.examprep.common.ForbiddenOperationException;
import com.examprep.common.NotFoundException;
import com.examprep.common.ValidationException;
import com.examprep.config.StorageProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Turns expected application failures into clear pages or JSON errors. */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final StorageProperties storageProperties;

    public GlobalExceptionHandler(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @ExceptionHandler(AppException.class)
    public Object handleAppException(AppException exception, HttpServletRequest request,
                                     HttpServletResponse response) {
        HttpStatus status = statusFor(exception);
        response.setStatus(status.value());
        return respond(request, status, exception.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleTooLarge(MaxUploadSizeExceededException exception, HttpServletRequest request,
                                 HttpServletResponse response) {
        HttpStatus status = HttpStatus.PAYLOAD_TOO_LARGE;
        response.setStatus(status.value());
        return respond(request, status, "That file is too large. The maximum upload size is "
                + storageProperties.getMaxFileSizeMb() + " MB.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request,
                                   HttpServletResponse response) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Please check the information you submitted.");
        HttpStatus status = HttpStatus.BAD_REQUEST;
        response.setStatus(status.value());
        return respond(request, status, message);
    }

    /**
     * Spring Security throws these from the service layer too, and the catch-all below used to turn them
     * into HTTP 500 pages before ExceptionTranslationFilter could react.
     */
    @ExceptionHandler(AuthenticationException.class)
    public Object handleAuthentication(AuthenticationException exception, HttpServletRequest request,
                                       HttpServletResponse response) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        response.setStatus(status.value());
        return respond(request, status, "Please sign in to continue.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException exception, HttpServletRequest request,
                                     HttpServletResponse response) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        response.setStatus(status.value());
        return respond(request, status, "You do not have access to that resource.");
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception exception, HttpServletRequest request,
                                   HttpServletResponse response) throws Exception {
        // Security exceptions must reach the security filter chain so it can redirect to sign-in or
        // render the access-denied page instead of a generic 500.
        if (exception instanceof AccessDeniedException || exception instanceof AuthenticationException) {
            throw exception;
        }
        log.error("Unexpected error while handling {}", request.getRequestURI(), exception);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        response.setStatus(status.value());
        return respond(request, status, "Something went wrong on our side. Please try again.");
    }

    private Object respond(HttpServletRequest request, HttpStatus status, String message) {
        if (isApiRequest(request)) {
            return ResponseEntity.status(status).body(Map.of("message",
                    message == null ? "Request failed." : message));
        }
        ModelAndView view = new ModelAndView("error/error");
        view.setStatus(status);
        view.addObject("status", status.value());
        view.addObject("message", message == null ? "Request failed." : message);
        return view;
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        String accept = request.getHeader("Accept");
        return path.startsWith("/api/") || (accept != null && accept.contains("application/json"));
    }

    private HttpStatus statusFor(AppException exception) {
        if (exception instanceof NotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (exception instanceof ForbiddenOperationException) {
            return HttpStatus.FORBIDDEN;
        }
        if (exception instanceof AiUnavailableException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (exception instanceof ValidationException) {
            return HttpStatus.BAD_REQUEST;
        }
        if (exception instanceof AiRateLimitException) {
            // An upstream quota/rate limit is not the student's mistake.
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (exception instanceof AiServiceException || exception instanceof AiResponseException) {
            return HttpStatus.BAD_GATEWAY;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
