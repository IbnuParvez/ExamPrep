package com.examprep.document.domain;

import com.examprep.api.document.DocumentProcessingStatus;
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
@Table(name = "document")
public class Document {

    private static final int FAILURE_REASON_LIMIT = 500;

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 160)
    private String contentType;

    @Column(name = "file_extension", nullable = false, length = 16)
    private String fileExtension;

    @Column(name = "storage_key", nullable = false, length = 320)
    private String storageKey;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "character_count")
    private Integer characterCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentProcessingStatus status;

    @Column(name = "failure_reason", length = FAILURE_REASON_LIMIT)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Document() {
        // for JPA
    }

    private Document(UUID id, UUID ownerId, String originalFilename, String contentType, String fileExtension,
                     String storageKey, long fileSizeBytes) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        this.id = id;
        this.ownerId = ownerId;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileExtension = fileExtension;
        this.storageKey = storageKey;
        this.fileSizeBytes = fileSizeBytes;
        this.status = DocumentProcessingStatus.UPLOADED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Document uploaded(UUID ownerId, String originalFilename, String contentType,
                                    DocumentFormat format, String storageKey, long fileSizeBytes) {
        return new Document(UUID.randomUUID(), ownerId, originalFilename, contentType, format.extension(),
                storageKey, fileSizeBytes);
    }

    public void markProcessing() {
        this.status = DocumentProcessingStatus.PROCESSING;
        this.failureReason = null;
        touch();
    }

    public void markReady(int pageCount, int characterCount) {
        this.status = DocumentProcessingStatus.READY;
        this.pageCount = pageCount > 0 ? pageCount : null;
        this.characterCount = characterCount;
        this.failureReason = null;
        touch();
    }

    public void markFailed(String reason) {
        this.status = DocumentProcessingStatus.FAILED;
        this.failureReason = truncate(reason);
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return "The document could not be processed.";
        }
        String trimmed = reason.trim();
        return trimmed.length() <= FAILURE_REASON_LIMIT ? trimmed : trimmed.substring(0, FAILURE_REASON_LIMIT);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public Integer getCharacterCount() {
        return characterCount;
    }

    public DocumentProcessingStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
