package com.examprep.web.controller;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.examprep.api.CurrentUserService;
import com.examprep.api.UserProfileService;
import com.examprep.api.dto.UpdateProfileRequest;
import com.examprep.api.dto.UserAccountDto;
import com.examprep.common.ValidationException;
import com.examprep.security.SessionAuthenticationRefresher;
import com.examprep.web.form.ProfileForm;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** The student's own account: profile edits and permanent deletion. */
@Controller
public class ProfileController {

    private static final String SESSION_COOKIE = "JSESSIONID";

    private final CurrentUserService currentUserService;
    private final UserProfileService profileService;
    private final SessionAuthenticationRefresher authenticationRefresher;

    public ProfileController(CurrentUserService currentUserService, UserProfileService profileService,
                             SessionAuthenticationRefresher authenticationRefresher) {
        this.currentUserService = currentUserService;
        this.profileService = profileService;
        this.authenticationRefresher = authenticationRefresher;
    }

    @GetMapping("/profile")
    public String profile(Model model, @RequestParam(value = "updated", required = false) String updated) {
        UserAccountDto account = profileService.getProfile(currentUserService.requireCurrentUserId());
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", ProfileForm.from(account));
        }
        model.addAttribute("account", account);
        if (updated != null) {
            model.addAttribute("notice", "Your profile has been updated.");
        }
        model.addAttribute("activeNav", "profile");
        return "profile/index";
    }

    @PostMapping("/profile")
    public String update(@Valid @ModelAttribute("form") ProfileForm form, BindingResult bindingResult,
                         HttpServletRequest request, Model model) {
        UUID userId = currentUserService.requireCurrentUserId();
        if (!form.passwordsMatch()) {
            bindingResult.rejectValue("newPasswordConfirm", "password.mismatch", "Passwords do not match.");
        }
        if (bindingResult.hasErrors()) {
            return renderWithErrors(model, userId);
        }
        try {
            profileService.updateProfile(userId, new UpdateProfileRequest(form.getFullName(), form.getEmail(),
                    form.getCurrentPassword(), form.getNewPassword()));
        } catch (ValidationException exception) {
            bindingResult.reject("profile.invalid", exception.getMessage());
            return renderWithErrors(model, userId);
        }
        // Refresh the principal so the header, the dashboard greeting and every other page show the new
        // details without asking the student to sign in again.
        authenticationRefresher.refresh(form.getEmail(), request);
        return "redirect:/profile?updated";
    }

    @PostMapping("/profile/delete")
    public String delete(@RequestParam(value = "currentPassword", required = false) String currentPassword,
                         HttpServletRequest request, HttpServletResponse response,
                         Authentication authentication, RedirectAttributes redirectAttributes) {
        UUID userId = currentUserService.requireCurrentUserId();
        try {
            profileService.deleteAccount(userId, currentPassword);
        } catch (ValidationException exception) {
            // Redirect back to a clean GET: the page's other form needs a real binding result to render.
            redirectAttributes.addFlashAttribute("deleteError", exception.getMessage());
            return "redirect:/profile";
        }
        // The account no longer exists, so the session that referred to it must end immediately.
        signOut(request, response, authentication);
        return "redirect:/login?deleted";
    }

    private String renderWithErrors(Model model, UUID userId) {
        model.addAttribute("account", profileService.getProfile(userId));
        if (model.getAttribute("form") instanceof ProfileForm form) {
            form.clearPasswords();
        }
        model.addAttribute("activeNav", "profile");
        return "profile/index";
    }

    private void signOut(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        Cookie sessionCookie = new Cookie(SESSION_COOKIE, "");
        sessionCookie.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
        sessionCookie.setMaxAge(0);
        sessionCookie.setHttpOnly(true);
        response.addCookie(sessionCookie);
    }
}
