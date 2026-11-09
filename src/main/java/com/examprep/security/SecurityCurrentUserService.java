package com.examprep.security;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.examprep.api.CurrentUserService;

@Service
public class SecurityCurrentUserService implements CurrentUserService {

    @Override
    public UUID requireCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityUser user)) {
            throw new AccessDeniedException("You must be signed in to perform this action.");
        }
        return user.getId();
    }
}
