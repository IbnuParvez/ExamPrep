package com.examprep.web.page;

import com.examprep.common.ValidationException;
import com.examprep.user.dto.RegisterUserRequest;
import com.examprep.user.service.UserRegistrationService;
import com.examprep.web.page.form.RegistrationForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserRegistrationService userRegistrationService;

    public AuthController(UserRegistrationService userRegistrationService) {
        this.userRegistrationService = userRegistrationService;
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error,
                            @RequestParam(required = false) String logout,
                            @RequestParam(required = false) String registered,
                            Model model) {
        model.addAttribute("loginError", error != null);
        model.addAttribute("loggedOut", logout != null);
        model.addAttribute("registered", registered != null);
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new RegistrationForm());
        }
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        if (form.getPassword() != null && !form.getPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch",
                    "The two passwords do not match.");
        }
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        try {
            userRegistrationService.register(new RegisterUserRequest(form.getFullName(), form.getEmail(),
                    form.getPassword()));
        } catch (ValidationException duplicate) {
            bindingResult.rejectValue("email", "email.exists", duplicate.getMessage());
            return "auth/register";
        }
        redirectAttributes.addFlashAttribute("registrationEmail", form.getEmail().trim());
        return "redirect:/login?registered";
    }
}
