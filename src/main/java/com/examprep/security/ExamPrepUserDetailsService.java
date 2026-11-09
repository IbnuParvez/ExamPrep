package com.examprep.security;

import com.examprep.user.domain.UserAccount;
import com.examprep.user.repository.UserAccountRepository;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamPrepUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    public ExamPrepUserDetailsService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return userAccountRepository.findByEmail(normalized)
                .map(ExamPrepUserDetailsService::toPrincipal)
                .orElseThrow(() -> new UsernameNotFoundException("No account was found for that email address."));
    }

    private static AppUserPrincipal toPrincipal(UserAccount account) {
        return new AppUserPrincipal(account.getId(), account.getEmail(), account.getFullName(),
                account.getPasswordHash(), true);
    }
}
