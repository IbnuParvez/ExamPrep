package com.examprep.study.domain;

import com.examprep.api.study.DefinitionDto;
import com.examprep.api.study.FormulaDto;
import com.examprep.api.study.RevisionSectionDto;
import com.examprep.api.study.StudyPackStatus;
import com.examprep.api.study.TopicDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "study_pack")
public class StudyPack {

    private static final int FAILURE_REASON_LIMIT = 500;

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StudyPackStatus status;

    @Column(name = "progress_message", length = 300)
    private String progressMessage;

    @Column(name = "failure_reason", length = FAILURE_REASON_LIMIT)
    private String failureReason;

    @Column(name = "language", nullable = false, length = 20)
    private String language;

    @Column(name = "academic_level", nullable = false, length = 40)
    private String academicLevel;

    @Column(name = "exam_date")
    private LocalDate examDate;

    @Column(name = "requested_question_count", nullable = false)
    private int requestedQuestionCount;

    @Column(name = "requested_flashcard_count", nullable = false)
    private int requestedFlashcardCount;

    @Column(name = "exam_duration_minutes", nullable = false)
    private int examDurationMinutes;

    @Column(name = "introduction", columnDefinition = "text")
    private String introduction;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "learning_objectives")
    private List<String> learningObjectives;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "revision_priorities")
    private List<String> revisionPriorities;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "topics")
    private List<TopicDto> topics;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "revision_notes")
    private List<RevisionSectionDto> revisionNotes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definitions")
    private List<DefinitionDto> definitions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "formulas")
    private List<FormulaDto> formulas;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_facts")
    private List<String> keyFacts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudyPack() {
        // for JPA
    }

    private StudyPack(UUID id, UUID ownerId, UUID documentId, String title, String language,
                      String academicLevel, LocalDate examDate, int requestedQuestionCount,
                      int requestedFlashcardCount, int examDurationMinutes) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        this.id = id;
        this.ownerId = ownerId;
        this.documentId = documentId;
        this.title = title;
        this.status = StudyPackStatus.QUEUED;
        this.language = language;
        this.academicLevel = academicLevel;
        this.examDate = examDate;
        this.requestedQuestionCount = requestedQuestionCount;
        this.requestedFlashcardCount = requestedFlashcardCount;
        this.examDurationMinutes = examDurationMinutes;
        this.createdAt = now;
        this.updatedAt = now;
        this.progressMessage = "Waiting to start...";
    }

    public static StudyPack queued(UUID ownerId, UUID documentId, String title, String language,
                                   String academicLevel, LocalDate examDate, int requestedQuestionCount,
                                   int requestedFlashcardCount, int examDurationMinutes) {
        return new StudyPack(UUID.randomUUID(), ownerId, documentId, truncate(title, 300),
                truncate(language, 20), truncate(academicLevel, 40), examDate,
                requestedQuestionCount, requestedFlashcardCount, examDurationMinutes);
    }

    public void markProcessing(String progress) {
        this.status = StudyPackStatus.PROCESSING;
        this.progressMessage = truncate(progress, 300);
        this.failureReason = null;
        touch();
    }

    public void updateProgress(String progress) {
        this.progressMessage = truncate(progress, 300);
        touch();
    }

    public void markReady(StudyPackContent content) {
        this.status = StudyPackStatus.READY;
        this.title = truncate(content.title(), 300);
        this.introduction = content.introduction();
        this.summary = content.summary();
        this.learningObjectives = content.learningObjectives();
        this.revisionPriorities = content.revisionPriorities();
        this.topics = content.topics();
        this.revisionNotes = content.revisionNotes();
        this.definitions = content.definitions();
        this.formulas = content.formulas();
        this.keyFacts = content.keyFacts();
        this.examDurationMinutes = content.examDurationMinutes();
        this.progressMessage = "Ready to study";
        this.failureReason = null;
        touch();
    }

    public void markFailed(String reason) {
        this.status = StudyPackStatus.FAILED;
        this.failureReason = truncate(reason, FAILURE_REASON_LIMIT);
        this.progressMessage = null;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getTitle() {
        return title;
    }

    public StudyPackStatus getStatus() {
        return status;
    }

    public String getProgressMessage() {
        return progressMessage;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getLanguage() {
        return language;
    }

    public String getAcademicLevel() {
        return academicLevel;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public int getRequestedQuestionCount() {
        return requestedQuestionCount;
    }

    public int getRequestedFlashcardCount() {
        return requestedFlashcardCount;
    }

    public int getExamDurationMinutes() {
        return examDurationMinutes;
    }

    public String getIntroduction() {
        return introduction;
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getLearningObjectives() {
        return learningObjectives;
    }

    public List<String> getRevisionPriorities() {
        return revisionPriorities;
    }

    public List<TopicDto> getTopics() {
        return topics;
    }

    public List<RevisionSectionDto> getRevisionNotes() {
        return revisionNotes;
    }

    public List<DefinitionDto> getDefinitions() {
        return definitions;
    }

    public List<FormulaDto> getFormulas() {
        return formulas;
    }

    public List<String> getKeyFacts() {
        return keyFacts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
