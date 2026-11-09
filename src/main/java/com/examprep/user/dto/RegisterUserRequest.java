package com.examprep.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration input. Validation mirrors the rules enforced in the browser form.
 */
public record RegisterUserRequest(
        @NotBlank(message = "Please enter your full name.")
        @Size(min = 2, max = 120, message = "Your name must be between 2 and 120 characters.")
        String fullName,

        @NotBlank(message = "Please enter your email address.")
        @Email(message = "Please enter a valid email address.")
        @Size(max = 254, message = "That email address is too long.")
        String email,

        @NotBlank(message = "Please choose a password.")
        @Size(min = 8, max = 100, message = "Your password must be between 8 and 100 characters.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Your password must include at least one letter and one number.")
        String password) {
}
