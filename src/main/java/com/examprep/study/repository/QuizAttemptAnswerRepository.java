package com.examprep.study.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.examprep.study.domain.QuizAttemptAnswer;

public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswer, UUID> {

    List<QuizAttemptAnswer> findByAttemptIdOrderByPositionAsc(UUID attemptId);
}
