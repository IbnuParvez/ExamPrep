package com.examprep.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Replaces the session's principal after the account details changed. Without this the header and the
 * dashboard greeting would keep showing the name captured at sign-in until the student signed out.
 */
@Component
public class SessionAuthenticationRefresher {

    private final ExamPrepUserDetailsService userDetailsService;

    public SessionAuthenticationRefresher(ExamPrepUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    public void refresh(String email, HttpServletRequest request) {
        UserDetails user = userDetailsService.loadUserByUsername(email);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user, null, user.getAuthorities());

        if (request.getSession(false) != null) {
            // A changed password should not leave the pre-change session identifier in use.
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }
}
