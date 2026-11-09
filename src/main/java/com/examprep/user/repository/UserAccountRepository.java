package com.examprep.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.examprep.user.domain.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
