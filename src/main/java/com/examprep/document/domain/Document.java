package com.examprep.document.domain;

import java.time.Instant;
import java.util.UUID;

import com.examprep.api.model.DocumentStatus;

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

/** An uploaded learning document owned by exactly one student. */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 200)
    private String contentType;

    @Column(name = "file_extension", nullable = false, length = 20)
    private String fileExtension;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "char_count")
    private Integer charCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Document() {
    }

    public static Document uploaded(UUID ownerId, String originalFilename, String contentType, String fileExtension,
                                    String storageKey, long fileSize) {
        Document document = new Document();
        document.ownerId = ownerId;
        document.originalFilename = originalFilename;
        document.contentType = contentType;
        document.fileExtension = fileExtension;
        document.storageKey = storageKey;
        document.fileSize = fileSize;
        document.status = DocumentStatus.UPLOADED;
        return document;
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

    public void markProcessing() {
        status = DocumentStatus.PROCESSING;
        failureReason = null;
    }

    public void markReady(int pages, int characters) {
        status = DocumentStatus.READY;
        failureReason = null;
        pageCount = pages;
        charCount = characters;
    }

    public void markFailed(String reason) {
        status = DocumentStatus.FAILED;
        failureReason = truncate(reason, 1000);
    }

    public void resetForRetry() {
        status = DocumentStatus.UPLOADED;
        failureReason = null;
        pageCount = null;
        charCount = null;
    }

    private static String truncate(String value, int max) {
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

    public long getFileSize() {
        return fileSize;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public Integer getCharCount() {
        return charCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** A short, readable document title derived from the original filename. */
    public String displayTitle() {
        int dot = originalFilename.lastIndexOf('.');
        String base = dot > 0 ? originalFilename.substring(0, dot) : originalFilename;
        return base.replace('_', ' ').trim();
    }
}
