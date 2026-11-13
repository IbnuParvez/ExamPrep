package com.examprep.web.form;

import com.examprep.api.dto.UserAccountDto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Browser profile form. Password fields are optional and never pre-filled. */
public class ProfileForm {

    @NotBlank(message = "Please enter your full name.")
    @Size(max = 120, message = "Your name must be 120 characters or fewer.")
    private String fullName;

    @NotBlank(message = "Please enter your email address.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 255, message = "That email address is too long.")
    private String email;

    private String currentPassword;

    private String newPassword;

    private String newPasswordConfirm;

    public static ProfileForm from(UserAccountDto account) {
        ProfileForm form = new ProfileForm();
        form.setFullName(account.fullName());
        form.setEmail(account.email());
        return form;
    }

    /** True when no password change was requested or both password boxes agree. */
    public boolean passwordsMatch() {
        if (newPassword == null || newPassword.isBlank()) {
            return true;
        }
        return newPassword.equals(newPasswordConfirm);
    }

    /** Passwords are never echoed back into the page after a failed submit. */
    public void clearPasswords() {
        this.currentPassword = null;
        this.newPassword = null;
        this.newPasswordConfirm = null;
    }

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

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getNewPasswordConfirm() {
        return newPasswordConfirm;
    }

    public void setNewPasswordConfirm(String newPasswordConfirm) {
        this.newPasswordConfirm = newPasswordConfirm;
    }
}
