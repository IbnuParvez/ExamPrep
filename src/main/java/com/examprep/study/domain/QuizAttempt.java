package com.examprep.study.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "quiz_attempt")
public class QuizAttempt {

    @Id
    private UUID id;

    @Column(name = "study_pack_id", nullable = false)
    private UUID studyPackId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 20)
    private QuizMode mode;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "time_taken_seconds", nullable = false)
    private int timeTakenSeconds;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    protected QuizAttempt() {
        // for JPA
    }

    public QuizAttempt(UUID studyPackId, UUID studentId, QuizMode mode, int score, int totalQuestions,
                       int timeTakenSeconds) {
        this.id = UUID.randomUUID();
        this.studyPackId = studyPackId;
        this.studentId = studentId;
        this.mode = mode;
        this.score = score;
        this.totalQuestions = totalQuestions;
        this.timeTakenSeconds = timeTakenSeconds;
        this.submittedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudyPackId() {
        return studyPackId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public QuizMode getMode() {
        return mode;
    }

    public int getScore() {
        return score;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public int getTimeTakenSeconds() {
        return timeTakenSeconds;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
