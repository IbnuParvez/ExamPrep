package com.examprep.study.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** A persisted tutor conversation message, owned by one student. */
@Entity
@Table(name = "tutor_messages")
public class TutorMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "study_pack_id", nullable = false)
    private UUID studyPackId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TutorRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_refs", columnDefinition = "jsonb")
    private List<String> sourceRefs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TutorMessageEntity() {
    }

    public static TutorMessageEntity user(UUID ownerId, UUID studyPackId, String content) {
        return user(ownerId, studyPackId, content, null);
    }

    public static TutorMessageEntity user(UUID ownerId, UUID studyPackId, String content, Instant createdAt) {
        return create(ownerId, studyPackId, TutorRole.USER, content, null, createdAt);
    }

    public static TutorMessageEntity assistant(UUID ownerId, UUID studyPackId, String content, List<String> sources) {
        return assistant(ownerId, studyPackId, content, sources, null);
    }

    public static TutorMessageEntity assistant(UUID ownerId, UUID studyPackId, String content, List<String> sources,
                                               Instant createdAt) {
        return create(ownerId, studyPackId, TutorRole.ASSISTANT, content, sources, createdAt);
    }

    private static TutorMessageEntity create(UUID ownerId, UUID studyPackId, TutorRole role, String content,
                                             List<String> sources, Instant createdAt) {
        TutorMessageEntity entity = new TutorMessageEntity();
        entity.ownerId = ownerId;
        entity.studyPackId = studyPackId;
        entity.role = role;
        entity.content = content;
        entity.sourceRefs = sources;
        entity.createdAt = createdAt;
        return entity;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
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

    public TutorRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public List<String> getSourceRefs() {
        return sourceRefs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
