package com.examprep.user.service;

import com.examprep.user.domain.UserAccount;
import com.examprep.user.dto.RegisterUserRequest;

/**
 * Shared account contract used by the web layer.
 */
public interface UserRegistrationService {

    /**
     * Creates a new student account with a BCrypt hashed password.
     *
     * @throws com.examprep.common.ValidationException when the email is already registered
     */
    UserAccount register(RegisterUserRequest request);
}
