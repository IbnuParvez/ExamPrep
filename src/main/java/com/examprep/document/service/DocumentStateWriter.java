package com.examprep.document.service;

import com.examprep.document.domain.Document;
import com.examprep.document.domain.DocumentChunk;
import com.examprep.document.repository.DocumentChunkRepository;
import com.examprep.document.repository.DocumentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional state changes for the asynchronous extraction pipeline. Each call is its own
 * transaction so a failure cannot leave a half-written document.
 */
@Component
public class DocumentStateWriter {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;

    public DocumentStateWriter(DocumentRepository documentRepository, DocumentChunkRepository chunkRepository) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
    }

    @Transactional
    public void markProcessing(UUID documentId) {
        documentRepository.findById(documentId).ifPresent(Document::markProcessing);
    }

    @Transactional
    public void markReady(UUID documentId, Integer pageCount, int characterCount, List<ChunkDraft> chunks) {
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            return;
        }
        chunkRepository.deleteAllForDocument(documentId);
        List<DocumentChunk> entities = new ArrayList<>(chunks.size());
        int index = 0;
        for (ChunkDraft chunk : chunks) {
            entities.add(new DocumentChunk(documentId, index++, chunk.referenceId(), chunk.locationLabel(),
                    chunk.heading(), chunk.text()));
        }
        chunkRepository.saveAll(entities);
        document.markReady(pageCount == null ? 0 : pageCount, characterCount);
    }

    @Transactional
    public void markFailed(UUID documentId, String reason) {
        documentRepository.findById(documentId).ifPresent(document -> document.markFailed(reason));
    }
}
