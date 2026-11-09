package com.examprep.study.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "flashcard")
public class Flashcard {

    @Id
    private UUID id;

    @Column(name = "study_pack_id", nullable = false)
    private UUID studyPackId;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "front", nullable = false, columnDefinition = "text")
    private String front;

    @Column(name = "back", nullable = false, columnDefinition = "text")
    private String back;

    @Column(name = "source_reference", length = 160)
    private String sourceReference;

    protected Flashcard() {
        // for JPA
    }

    public Flashcard(UUID studyPackId, int position, String front, String back, String sourceReference) {
        this.id = UUID.randomUUID();
        this.studyPackId = studyPackId;
        this.position = position;
        this.front = front;
        this.back = back;
        this.sourceReference = sourceReference;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudyPackId() {
        return studyPackId;
    }

    public int getPosition() {
        return position;
    }

    public String getFront() {
        return front;
    }

    public String getBack() {
        return back;
    }

    public String getSourceReference() {
        return sourceReference;
    }
}
