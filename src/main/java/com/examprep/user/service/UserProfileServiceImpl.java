package com.examprep.user.service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.examprep.api.DocumentApplicationService;
import com.examprep.api.UserProfileService;
import com.examprep.api.dto.UpdateProfileRequest;
import com.examprep.api.dto.UserAccountDto;
import com.examprep.common.NotFoundException;
import com.examprep.common.ValidationException;
import com.examprep.user.domain.UserAccount;
import com.examprep.user.repository.UserAccountRepository;

@Service
@Transactional
public class UserProfileServiceImpl implements UserProfileService {

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final DocumentApplicationService documentService;

    public UserProfileServiceImpl(UserAccountRepository repository, PasswordEncoder passwordEncoder,
                                  DocumentApplicationService documentService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.documentService = documentService;
    }

    @Override
    @Transactional(readOnly = true)
    public UserAccountDto getProfile(UUID userId) {
        return toDto(require(userId));
    }

    @Override
    public UserAccountDto updateProfile(UUID userId, UpdateProfileRequest request) {
        UserAccount account = require(userId);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String newPassword = request.newPassword();

        if (repository.existsByEmailIgnoreCaseAndIdNot(email, userId)) {
            throw new ValidationException("Another account already uses that email address.");
        }
        if (newPassword != null && !newPassword.isBlank()) {
            if (newPassword.length() < 8 || newPassword.length() > 100) {
                throw new ValidationException("Passwords must be between 8 and 100 characters.");
            }
            if (request.currentPassword() == null || request.currentPassword().isBlank()
                    || !passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
                throw new ValidationException("Your current password is not correct, so the new password was not saved.");
            }
            account.changePassword(passwordEncoder.encode(newPassword));
        }

        account.updateProfile(request.fullName().trim(), email);
        try {
            repository.saveAndFlush(account);
        } catch (DataIntegrityViolationException violation) {
            // The unique index on lower(email) is the authority when two edits race each other.
            throw new ValidationException("Another account already uses that email address.");
        }
        return toDto(account);
    }

    @Override
    public void deleteAccount(UUID userId, String currentPassword) {
        UserAccount account = require(userId);
        if (currentPassword == null || currentPassword.isBlank()
                || !passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new ValidationException("Your password is not correct. Your account has not been deleted.");
        }
        // The database removes owned rows through ON DELETE CASCADE, but uploaded files live outside it.
        documentService.purgeStoredFiles(userId);
        repository.delete(account);
        repository.flush();
    }

    private UserAccount require(UUID userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Your account could not be found."));
    }

    private UserAccountDto toDto(UserAccount account) {
        return new UserAccountDto(account.getId(), account.getFullName(), account.getEmail());
    }
}
