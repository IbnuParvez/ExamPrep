package com.examprep.user.service;

import com.examprep.common.ValidationException;
import com.examprep.user.domain.UserAccount;
import com.examprep.user.dto.RegisterUserRequest;
import com.examprep.user.repository.UserAccountRepository;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserRegistrationServiceImpl implements UserRegistrationService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public UserRegistrationServiceImpl(UserAccountRepository userAccountRepository,
                                       PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public UserAccount register(RegisterUserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userAccountRepository.existsByEmail(email)) {
            throw new ValidationException("An account with that email address already exists.");
        }
        try {
            UserAccount account = UserAccount.create(
                    request.fullName().trim(),
                    email,
                    passwordEncoder.encode(request.password()));
            return userAccountRepository.save(account);
        } catch (DataIntegrityViolationException duplicate) {
            // A concurrent registration won the race on the unique index.
            throw new ValidationException("An account with that email address already exists.");
        }
    }
}
