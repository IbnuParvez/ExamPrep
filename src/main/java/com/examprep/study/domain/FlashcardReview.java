package com.examprep.study.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "flashcard_review")
public class FlashcardReview {

    @Id
    private UUID id;

    @Column(name = "study_pack_id", nullable = false)
    private UUID studyPackId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "flashcard_id", nullable = false)
    private UUID flashcardId;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    protected FlashcardReview() {
        // for JPA
    }

    public FlashcardReview(UUID studyPackId, UUID studentId, UUID flashcardId) {
        this.id = UUID.randomUUID();
        this.studyPackId = studyPackId;
        this.studentId = studentId;
        this.flashcardId = flashcardId;
        this.reviewedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
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

    public UUID getFlashcardId() {
        return flashcardId;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
