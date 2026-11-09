package com.examprep.web.advice;

import com.examprep.ai.exception.AiConfigurationException;
import com.examprep.ai.exception.AiGenerationException;
import com.examprep.ai.exception.AiUnavailableException;
import com.examprep.common.ResourceNotFoundException;
import com.examprep.common.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.ModelAndView;

/**
 * Renders friendly HTML error pages for failures that happen outside JSON endpoints.
 */
@org.springframework.web.bind.annotation.ControllerAdvice(
        basePackages = {"com.examprep.web.page", "com.examprep.web.advice"})
public class PageExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PageExceptionHandler.class);

    @org.springframework.web.bind.annotation.ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView notFound(ResourceNotFoundException notFound) {
        return page("error/404", 404, "We could not find that page",
                notFound.getMessage(), null);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(ValidationException.class)
    public ModelAndView invalid(ValidationException invalid) {
        return page("error/message", 400, "That action could not be completed", invalid.getMessage(), null);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(AccessDeniedException.class)
    public ModelAndView forbidden(AccessDeniedException denied) {
        return page("error/message", 403, "This page is protected",
                "You need to sign in with the account that owns this material.", null);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(AiConfigurationException.class)
    public ModelAndView aiNotConfigured(AiConfigurationException configuration) {
        return page("error/ai", 503, "AI features are not configured",
                configuration.getMessage(),
                "The rest of ExamPrep still works. Add the key, restart the application and try again.");
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(AiUnavailableException.class)
    public ModelAndView aiUnavailable(AiUnavailableException unavailable) {
        return page("error/ai", 503, "The AI service is unavailable", unavailable.getMessage(),
                "Your document is safe. Try the action again in a moment.");
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(AiGenerationException.class)
    public ModelAndView aiInvalid(AiGenerationException invalid) {
        return page("error/ai", 502, "The study material could not be generated",
                "The AI response could not be validated, so nothing was saved.",
                "Try generating the study package again. If it keeps failing, simplify or shorten the document.");
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadTooLarge() {
        return "redirect:/documents?uploadError=size";
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(MultipartException.class)
    public String uploadFailed(MultipartException failure) {
        log.warn("Multipart upload failed: {}", failure.getClass().getSimpleName());
        return "redirect:/documents?uploadError=invalid";
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
    public ModelAndView unexpected(Exception failure, HttpServletRequest request) {
        log.error("Unhandled error on {}", request.getRequestURI(), failure);
        return page("error/500", 500, "Something went wrong",
                "The request could not be completed. The problem has been logged.", null);
    }

    private ModelAndView page(String view, int status, String title, String message, String hint) {
        ModelAndView modelAndView = new ModelAndView(view);
        modelAndView.setStatus(org.springframework.http.HttpStatusCode.valueOf(status));
        modelAndView.addObject("title", title);
        modelAndView.addObject("message", message);
        modelAndView.addObject("hint", hint);
        return modelAndView;
    }
}
