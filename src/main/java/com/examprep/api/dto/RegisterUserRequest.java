package com.examprep.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Registration form payload. */
public record RegisterUserRequest(
        @NotBlank(message = "Please enter your full name.")
        @Size(max = 120, message = "Your name must be 120 characters or fewer.")
        String fullName,

        @NotBlank(message = "Please enter your email address.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 255, message = "That email address is too long.")
        String email,

        @NotBlank(message = "Please choose a password.")
        @Size(min = 8, max = 100, message = "Passwords must be between 8 and 100 characters.")
        String password
) {
}
