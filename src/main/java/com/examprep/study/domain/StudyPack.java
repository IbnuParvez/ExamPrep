package com.examprep.study.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.examprep.api.dto.StudyPreferences;
import com.examprep.api.model.StudyPackStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** A generated study package and its processing state. */
@Entity
@Table(name = "study_packs")
public class StudyPack {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(nullable = false, length = 300)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudyPackStatus status;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "progress_message", length = 300)
    private String progressMessage;

    @Column(name = "progress_percent", nullable = false)
    private int progressPercent;

    @Column(length = 40)
    private String language;

    @Column(name = "academic_level", length = 60)
    private String academicLevel;

    @Column(name = "exam_date")
    private LocalDate examDate;

    @Column(name = "question_target")
    private Integer questionTarget;

    @Column(name = "flashcard_target")
    private Integer flashcardTarget;

    @Column(name = "exam_question_count")
    private Integer examQuestionCount;

    @Column(name = "exam_duration_minutes")
    private Integer examDurationMinutes;

    @Column(columnDefinition = "text")
    private String introduction;

    @Column(columnDefinition = "text")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> objectives;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "revision_areas", columnDefinition = "jsonb")
    private List<String> revisionAreas;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudyPack() {
    }

    public static StudyPack queued(UUID ownerId, UUID documentId, String title, StudyPreferences preferences) {
        StudyPack pack = new StudyPack();
        pack.ownerId = ownerId;
        pack.documentId = documentId;
        pack.title = title;
        pack.status = StudyPackStatus.QUEUED;
        pack.progressMessage = "Waiting to start";
        pack.progressPercent = 5;
        pack.language = preferences.language();
        pack.academicLevel = preferences.academicLevel();
        pack.examDate = preferences.examDate();
        pack.questionTarget = preferences.questionCount();
        pack.flashcardTarget = preferences.flashcardCount();
        pack.examQuestionCount = preferences.examQuestionCount();
        pack.examDurationMinutes = preferences.examDurationMinutes();
        return pack;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markProcessing(String message, int percent) {
        status = StudyPackStatus.PROCESSING;
        progressMessage = trim(message, 300);
        progressPercent = Math.max(5, Math.min(99, percent));
        failureReason = null;
    }

    /** Re-queues a FAILED package for generation, reusing the preferences stored on it. */
    public void resetForRetry() {
        status = StudyPackStatus.QUEUED;
        progressMessage = "Waiting to start";
        progressPercent = 5;
        failureReason = null;
    }

    public void markReady() {
        status = StudyPackStatus.READY;
        progressMessage = "Study package ready";
        progressPercent = 100;
        failureReason = null;
    }

    public void markFailed(String reason) {
        status = StudyPackStatus.FAILED;
        progressMessage = null;
        progressPercent = 0;
        failureReason = trim(reason, 1000);
    }

    public void applyGeneratedContent(String generatedTitle, String generatedIntroduction, String generatedSummary,
                                      List<String> learningObjectives, List<String> keyAreasToRevise,
                                      Map<String, Object> generatedContent) {
        if (generatedTitle != null && !generatedTitle.isBlank()) {
            title = trim(generatedTitle, 300);
        }
        introduction = generatedIntroduction;
        summary = generatedSummary;
        objectives = learningObjectives;
        revisionAreas = keyAreasToRevise;
        content = generatedContent;
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
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

    public String getFailureReason() {
        return failureReason;
    }

    public String getProgressMessage() {
        return progressMessage;
    }

    public int getProgressPercent() {
        return progressPercent;
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

    public Integer getQuestionTarget() {
        return questionTarget;
    }

    public Integer getFlashcardTarget() {
        return flashcardTarget;
    }

    public Integer getExamQuestionCount() {
        return examQuestionCount;
    }

    public Integer getExamDurationMinutes() {
        return examDurationMinutes;
    }

    public String getIntroduction() {
        return introduction;
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getObjectives() {
        return objectives;
    }

    public List<String> getRevisionAreas() {
        return revisionAreas;
    }

    public Map<String, Object> getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
