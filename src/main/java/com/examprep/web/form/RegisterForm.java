package com.examprep.web.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Browser registration form. */
public class RegisterForm {

    @NotBlank(message = "Please enter your full name.")
    @Size(max = 120, message = "Your name must be 120 characters or fewer.")
    private String fullName;

    @NotBlank(message = "Please enter your email address.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 255, message = "That email address is too long.")
    private String email;

    @NotBlank(message = "Please choose a password.")
    @Size(min = 8, max = 100, message = "Passwords must be between 8 and 100 characters.")
    private String password;

    private String passwordConfirm;

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

    public String getPasswordConfirm() {
        return passwordConfirm;
    }

    public void setPasswordConfirm(String passwordConfirm) {
        this.passwordConfirm = passwordConfirm;
    }
}
