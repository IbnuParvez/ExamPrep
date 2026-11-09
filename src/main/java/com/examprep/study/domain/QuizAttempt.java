package com.examprep.study.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** One mock examination attempt. */
@Entity
@Table(name = "quiz_attempts")
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "study_pack_id", nullable = false)
    private UUID studyPackId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttemptKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttemptStatus status;

    @Column(nullable = false)
    private int score;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "time_taken_seconds")
    private Integer timeTakenSeconds;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    protected QuizAttempt() {
    }

    public static QuizAttempt start(UUID ownerId, UUID studyPackId, int totalQuestions, int durationSeconds) {
        QuizAttempt attempt = new QuizAttempt();
        attempt.ownerId = ownerId;
        attempt.studyPackId = studyPackId;
        attempt.kind = AttemptKind.MOCK_EXAM;
        attempt.status = AttemptStatus.IN_PROGRESS;
        attempt.totalQuestions = totalQuestions;
        attempt.durationSeconds = Math.max(60, durationSeconds);
        attempt.score = 0;
        return attempt;
    }

    @PrePersist
    void onCreate() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }

    public void submit(int finalScore, int secondsTaken) {
        this.score = Math.max(0, finalScore);
        this.timeTakenSeconds = Math.max(0, secondsTaken);
        this.status = AttemptStatus.SUBMITTED;
        this.submittedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getStudyPackId() {
        return studyPackId;
    }

    public AttemptKind getKind() {
        return kind;
    }

    public AttemptStatus getStatus() {
        return status;
    }

    public int getScore() {
        return score;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public Integer getTimeTakenSeconds() {
        return timeTakenSeconds;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
