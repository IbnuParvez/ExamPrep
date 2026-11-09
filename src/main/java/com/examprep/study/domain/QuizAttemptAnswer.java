package com.examprep.study.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The student's answer to one examination question. */
@Entity
@Table(name = "quiz_attempt_answers")
public class QuizAttemptAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(nullable = false)
    private int position;

    @Column(name = "selected_option_index")
    private Integer selectedOptionIndex;

    @Column(name = "boolean_answer")
    private Boolean booleanAnswer;

    @Column(name = "text_answer", columnDefinition = "text")
    private String textAnswer;

    @Column(nullable = false)
    private boolean correct;

    protected QuizAttemptAnswer() {
    }

    public static QuizAttemptAnswer pending(UUID attemptId, UUID questionId, int position) {
        QuizAttemptAnswer answer = new QuizAttemptAnswer();
        answer.attemptId = attemptId;
        answer.questionId = questionId;
        answer.position = position;
        return answer;
    }

    public void record(Integer optionIndex, Boolean booleanValue, String text, boolean isCorrect) {
        this.selectedOptionIndex = optionIndex;
        this.booleanAnswer = booleanValue;
        this.textAnswer = text;
        this.correct = isCorrect;
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

    public int getPosition() {
        return position;
    }

    public Integer getSelectedOptionIndex() {
        return selectedOptionIndex;
    }

    public Boolean getBooleanAnswer() {
        return booleanAnswer;
    }

    public String getTextAnswer() {
        return textAnswer;
    }

    public boolean isCorrect() {
        return correct;
    }
}
