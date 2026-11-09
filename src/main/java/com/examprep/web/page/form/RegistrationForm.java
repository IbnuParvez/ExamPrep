package com.examprep.web.page.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Browser registration form. Converted to {@link com.examprep.user.dto.RegisterUserRequest}
 * before reaching the service layer.
 */
public class RegistrationForm {

    @NotBlank(message = "Please enter your full name.")
    @Size(min = 2, max = 120, message = "Your name must be between 2 and 120 characters.")
    private String fullName;

    @NotBlank(message = "Please enter your email address.")
    @Email(message = "Please enter a valid email address.")
    @Size(max = 254, message = "That email address is too long.")
    private String email;

    @NotBlank(message = "Please choose a password.")
    @Size(min = 8, max = 100, message = "Your password must be between 8 and 100 characters.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "Your password must include at least one letter and one number.")
    private String password;

    @NotBlank(message = "Please confirm your password.")
    private String confirmPassword;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
