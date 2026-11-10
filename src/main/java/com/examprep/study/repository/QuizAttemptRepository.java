package com.examprep.study.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.examprep.study.domain.AttemptStatus;
import com.examprep.study.domain.QuizAttempt;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    Optional<QuizAttempt> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<QuizAttempt> findByOwnerIdAndStatusOrderBySubmittedAtDesc(UUID ownerId, AttemptStatus status,
                                                                   Pageable pageable);

    List<QuizAttempt> findByOwnerIdOrderByStartedAtDesc(UUID ownerId, Pageable pageable);

    long countByOwnerIdAndStatus(UUID ownerId, AttemptStatus status);

    /** Best and average score across every submitted attempt, not only the ones shown on the dashboard. */
    @Query("""
            select coalesce(max(100.0 * a.score / a.totalQuestions), 0)
              from QuizAttempt a
             where a.ownerId = :ownerId and a.status = :status and a.totalQuestions > 0
            """)
    double maxPercentage(@Param("ownerId") UUID ownerId, @Param("status") AttemptStatus status);

    @Query("""
            select coalesce(avg(100.0 * a.score / a.totalQuestions), 0)
              from QuizAttempt a
             where a.ownerId = :ownerId and a.status = :status and a.totalQuestions > 0
            """)
    double averagePercentage(@Param("ownerId") UUID ownerId, @Param("status") AttemptStatus status);
}
