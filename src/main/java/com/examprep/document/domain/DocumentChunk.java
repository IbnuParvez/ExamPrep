package com.examprep.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "document_chunk")
public class DocumentChunk {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "reference_id", nullable = false, length = 16)
    private String referenceId;

    @Column(name = "location_label", nullable = false, length = 160)
    private String locationLabel;

    @Column(name = "heading", length = 300)
    private String heading;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "character_count", nullable = false)
    private int characterCount;

    protected DocumentChunk() {
        // for JPA
    }

    public DocumentChunk(UUID documentId, int chunkIndex, String referenceId, String locationLabel,
                         String heading, String content) {
        this.id = UUID.randomUUID();
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.referenceId = referenceId;
        this.locationLabel = locationLabel;
        this.heading = heading;
        this.content = content;
        this.characterCount = content.length();
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getLocationLabel() {
        return locationLabel;
    }

    public String getHeading() {
        return heading;
    }

    public String getContent() {
        return content;
    }

    public int getCharacterCount() {
        return characterCount;
    }
}
