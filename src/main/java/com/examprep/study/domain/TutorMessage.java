package com.examprep.study.domain;

import com.examprep.api.document.SourceReferenceDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tutor_message")
public class TutorMessage {

    @Id
    private UUID id;

    @Column(name = "study_pack_id", nullable = false)
    private UUID studyPackId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private TutorRole role;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_references")
    private List<SourceReferenceDto> sourceReferences;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TutorMessage() {
        // for JPA
    }

    private TutorMessage(UUID studyPackId, UUID studentId, TutorRole role, String content,
                         List<SourceReferenceDto> sourceReferences) {
        this.id = UUID.randomUUID();
        this.studyPackId = studyPackId;
        this.studentId = studentId;
        this.role = role;
        this.content = content;
        this.sourceReferences = sourceReferences;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    public static TutorMessage userQuestion(UUID studyPackId, UUID studentId, String content) {
        return new TutorMessage(studyPackId, studentId, TutorRole.USER, content, List.of());
    }

    public static TutorMessage assistantAnswer(UUID studyPackId, UUID studentId, String content,
                                               List<SourceReferenceDto> references) {
        return new TutorMessage(studyPackId, studentId, TutorRole.ASSISTANT, content, references);
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

    public TutorRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public List<SourceReferenceDto> getSourceReferences() {
        return sourceReferences;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
