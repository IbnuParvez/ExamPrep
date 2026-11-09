package com.examprep.study.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A persisted revision flashcard. */
@Entity
@Table(name = "flashcards")
public class FlashcardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "study_pack_id", nullable = false)
    private UUID studyPackId;

    @Column(nullable = false)
    private int ordinal;

    @Column(nullable = false, columnDefinition = "text")
    private String front;

    @Column(nullable = false, columnDefinition = "text")
    private String back;

    @Column(name = "source_ref", length = 200)
    private String sourceRef;

    protected FlashcardEntity() {
    }

    public static FlashcardEntity of(UUID studyPackId, int ordinal, String front, String back, String sourceRef) {
        FlashcardEntity entity = new FlashcardEntity();
        entity.studyPackId = studyPackId;
        entity.ordinal = ordinal;
        entity.front = front;
        entity.back = back;
        entity.sourceRef = sourceRef;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudyPackId() {
        return studyPackId;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public String getFront() {
        return front;
    }

    public String getBack() {
        return back;
    }

    public String getSourceRef() {
        return sourceRef;
    }
}
