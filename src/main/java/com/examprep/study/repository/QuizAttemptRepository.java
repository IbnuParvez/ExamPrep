package com.examprep.study.repository;

import com.examprep.study.domain.QuizAttempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    List<QuizAttempt> findByStudentIdOrderBySubmittedAtDesc(UUID studentId);

    List<QuizAttempt> findTop5ByStudentIdOrderBySubmittedAtDesc(UUID studentId);

    Optional<QuizAttempt> findByIdAndStudyPackIdAndStudentId(UUID id, UUID studyPackId, UUID studentId);

    long countByStudentId(UUID studentId);

    long countByStudyPackId(UUID studyPackId);
}
