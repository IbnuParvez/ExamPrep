package com.examprep.study.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "quiz_answer")
public class QuizAnswer {

    @Id
    private UUID id;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "given_answer", columnDefinition = "text")
    private String givenAnswer;

    @Column(name = "correct", nullable = false)
    private boolean correct;

    protected QuizAnswer() {
        // for JPA
    }

    public QuizAnswer(UUID attemptId, UUID questionId, String givenAnswer, boolean correct) {
        this.id = UUID.randomUUID();
        this.attemptId = attemptId;
        this.questionId = questionId;
        this.givenAnswer = givenAnswer;
        this.correct = correct;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public String getGivenAnswer() {
        return givenAnswer;
    }

    public boolean isCorrect() {
        return correct;
    }
}
