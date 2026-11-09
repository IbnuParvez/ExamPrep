package com.examprep.document.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A stable, citable unit of extracted document text. */
@Entity
@Table(name = "document_chunks")
public class DocumentChunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "ref_id", nullable = false, length = 40)
    private String refId;

    @Column(nullable = false)
    private int ordinal;

    @Column(name = "location_label", nullable = false, length = 200)
    private String locationLabel;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "char_count", nullable = false)
    private int charCount;

    protected DocumentChunkEntity() {
    }

    public static DocumentChunkEntity of(UUID documentId, String refId, int ordinal, String locationLabel,
                                         String content) {
        DocumentChunkEntity entity = new DocumentChunkEntity();
        entity.documentId = documentId;
        entity.refId = refId;
        entity.ordinal = ordinal;
        entity.locationLabel = locationLabel;
        entity.content = content;
        entity.charCount = content.length();
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getRefId() {
        return refId;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public String getLocationLabel() {
        return locationLabel;
    }

    public String getContent() {
        return content;
    }

    public int getCharCount() {
        return charCount;
    }
}
