package com.examprep.web.advice;

import com.examprep.security.CurrentUserService;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Shared values used by every page layout.
 */
@ControllerAdvice(basePackages = "com.examprep.web.page")
public class GlobalModelAdvice {

    private final CurrentUserService currentUserService;
    private final ViewFormatting viewFormatting;

    public GlobalModelAdvice(CurrentUserService currentUserService, ViewFormatting viewFormatting) {
        this.currentUserService = currentUserService;
        this.viewFormatting = viewFormatting;
    }

    @ModelAttribute("fmt")
    public ViewFormatting viewFormatting() {
        return viewFormatting;
    }

    @ModelAttribute("currentUser")
    public String currentUser() {
        return currentUserService.currentDisplayName();
    }

    @ModelAttribute("currentYear")
    public int currentYear() {
        return LocalDate.now().getYear();
    }
}
