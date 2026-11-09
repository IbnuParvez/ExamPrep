package com.examprep.web.controller;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.examprep.api.UserRegistrationService;
import com.examprep.api.dto.RegisterUserRequest;
import com.examprep.common.ValidationException;
import com.examprep.security.SecurityUser;
import com.examprep.web.form.RegisterForm;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@Controller
public class AuthController {

    private final UserRegistrationService registrationService;
    private final AuthenticationManager authenticationManager;

    public AuthController(UserRegistrationService registrationService, AuthenticationManager authenticationManager) {
        this.registrationService = registrationService;
        this.authenticationManager = authenticationManager;
    }

    @GetMapping("/login")
    public String login(Authentication authentication) {
        if (isSignedIn(authentication)) {
            return "redirect:/dashboard";
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model, Authentication authentication) {
        if (isSignedIn(authentication)) {
            return "redirect:/dashboard";
        }
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new RegisterForm());
        }
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult bindingResult,
                           HttpServletRequest request) {
        if (form.getPasswordConfirm() == null || !form.getPasswordConfirm().equals(form.getPassword())) {
            bindingResult.rejectValue("passwordConfirm", "password.mismatch", "Passwords do not match.");
        }
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        try {
            registrationService.register(new RegisterUserRequest(form.getFullName(), form.getEmail(),
                    form.getPassword()));
        } catch (ValidationException exception) {
            bindingResult.rejectValue("email", "email.duplicate", exception.getMessage());
            return "auth/register";
        }
        signIn(form.getEmail(), form.getPassword(), request);
        return "redirect:/dashboard";
    }

    /** Signs the new student in without asking them to type their password again. */
    private void signIn(String email, String rawPassword, HttpServletRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, rawPassword));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    private boolean isSignedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof SecurityUser;
    }
}
