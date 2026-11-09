package com.examprep.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Access point for the identity of the authenticated student.
 */
@Component
public class CurrentUserService {

    public UUID requireCurrentUserId() {
        return currentUserId()
                .orElseThrow(() -> new AccessDeniedException("You must sign in to access this resource."));
    }

    public Optional<UUID> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return Optional.of(principal.userId());
        }
        return Optional.empty();
    }

    public String currentDisplayName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return principal.fullName();
        }
        return "";
    }
}
