package com.examprep.study.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.examprep.study.domain.AttemptStatus;
import com.examprep.study.domain.QuizAttempt;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    Optional<QuizAttempt> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<QuizAttempt> findByOwnerIdAndStatusOrderBySubmittedAtDesc(UUID ownerId, AttemptStatus status,
                                                                   Pageable pageable);

    List<QuizAttempt> findByOwnerIdOrderByStartedAtDesc(UUID ownerId, Pageable pageable);

    long countByOwnerIdAndStatus(UUID ownerId, AttemptStatus status);
}
