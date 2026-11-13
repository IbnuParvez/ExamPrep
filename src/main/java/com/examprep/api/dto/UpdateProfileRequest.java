package com.examprep.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Profile edit payload. The password fields are optional: when {@code newPassword} is blank the stored
 * password is left untouched.
 */
public record UpdateProfileRequest(
        @NotBlank(message = "Please enter your full name.")
        @Size(max = 120, message = "Your name must be 120 characters or fewer.")
        String fullName,

        @NotBlank(message = "Please enter your email address.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 255, message = "That email address is too long.")
        String email,

        String currentPassword,

        String newPassword
) {
}
