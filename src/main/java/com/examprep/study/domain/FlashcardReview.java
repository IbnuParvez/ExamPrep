package com.examprep.study.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** A completed flashcard review session, used for the simple progress overview. */
@Entity
@Table(name = "flashcard_reviews")
public class FlashcardReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "study_pack_id", nullable = false)
    private UUID studyPackId;

    @Column(name = "reviewed_cards", nullable = false)
    private int reviewedCards;

    @Column(name = "total_cards", nullable = false)
    private int totalCards;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    protected FlashcardReview() {
    }

    public static FlashcardReview of(UUID ownerId, UUID studyPackId, int reviewedCards, int totalCards) {
        FlashcardReview review = new FlashcardReview();
        review.ownerId = ownerId;
        review.studyPackId = studyPackId;
        review.reviewedCards = reviewedCards;
        review.totalCards = totalCards;
        return review;
    }

    @PrePersist
    void onCreate() {
        if (reviewedAt == null) {
            reviewedAt = Instant.now();
        }
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

    public int getReviewedCards() {
        return reviewedCards;
    }

    public int getTotalCards() {
        return totalCards;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
