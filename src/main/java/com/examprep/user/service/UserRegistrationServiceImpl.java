package com.examprep.user.service;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.examprep.api.UserRegistrationService;
import com.examprep.api.dto.RegisterUserRequest;
import com.examprep.api.dto.UserAccountDto;
import com.examprep.common.ValidationException;
import com.examprep.user.domain.UserAccount;
import com.examprep.user.repository.UserAccountRepository;

@Service
@Transactional
public class UserRegistrationServiceImpl implements UserRegistrationService {

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserRegistrationServiceImpl(UserAccountRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserAccountDto register(RegisterUserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new ValidationException("An account with this email address already exists. Try signing in instead.");
        }
        UserAccount account = UserAccount.create(
                request.fullName().trim(),
                email,
                passwordEncoder.encode(request.password()));
        try {
            repository.saveAndFlush(account);
        } catch (DataIntegrityViolationException violation) {
            // Two simultaneous registrations can both pass the existsByEmail check; the unique index is
            // the authority, and the student deserves a form error rather than an HTTP 500 page.
            throw new ValidationException(
                    "An account with this email address already exists. Try signing in instead.");
        }
        return new UserAccountDto(account.getId(), account.getFullName(), account.getEmail());
    }
}
