package com.examprep.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.examprep.user.domain.UserAccount;
import com.examprep.user.repository.UserAccountRepository;

@Service
@Transactional(readOnly = true)
public class ExamPrepUserDetailsService implements UserDetailsService {

    private final UserAccountRepository repository;

    public ExamPrepUserDetailsService(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount account = repository.findByEmailIgnoreCase(username == null ? "" : username.trim())
                .orElseThrow(() -> new UsernameNotFoundException("No account found for those credentials."));
        return new SecurityUser(account.getId(), account.getFullName(), account.getEmail(), account.getPasswordHash());
    }
}
