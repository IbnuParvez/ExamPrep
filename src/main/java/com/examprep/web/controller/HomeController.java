package com.examprep.web.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.examprep.config.AiProperties;
import com.examprep.security.SecurityUser;

import org.springframework.ui.Model;

@Controller
public class HomeController {

    private final AiProperties aiProperties;

    public HomeController(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @GetMapping("/")
    public String landing(Authentication authentication, Model model) {
        if (isSignedIn(authentication)) {
            return "redirect:/dashboard";
        }
        // Only a boolean is exposed; the key itself never reaches the view.
        model.addAttribute("aiConfigured", aiProperties.isConfigured());
        return "index";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    /**
     * Spring Security forwards denied requests here, including POSTs that failed CSRF validation, so
     * this mapping must accept every method; a GET-only handler answered 405 for those forwards.
     */
    @RequestMapping("/error/403")
    public String forbidden() {
        return "error/403";
    }

    private boolean isSignedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof SecurityUser;
    }
}
