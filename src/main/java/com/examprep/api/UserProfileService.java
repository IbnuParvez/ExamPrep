package com.examprep.api;

import java.util.UUID;

import com.examprep.api.dto.UpdateProfileRequest;
import com.examprep.api.dto.UserAccountDto;

/** Reads and changes the signed-in student's own account, including irreversible deletion. */
public interface UserProfileService {

    UserAccountDto getProfile(UUID userId);

    /**
     * Applies name, email and (optionally) password changes. A password change requires the current
     * password; an email change must stay unique.
     */
    UserAccountDto updateProfile(UUID userId, UpdateProfileRequest request);

    /**
     * Permanently deletes the account, its stored files and every row that belongs to it. The caller is
     * responsible for ending the session afterwards.
     */
    void deleteAccount(UUID userId, String currentPassword);
}
