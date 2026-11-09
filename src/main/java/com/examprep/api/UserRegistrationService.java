package com.examprep.api;

import com.examprep.api.dto.RegisterUserRequest;
import com.examprep.api.dto.UserAccountDto;

/** Creates student accounts with BCrypt-hashed passwords. */
public interface UserRegistrationService {

    UserAccountDto register(RegisterUserRequest request);
}
