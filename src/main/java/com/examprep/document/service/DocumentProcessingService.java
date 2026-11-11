package com.examprep.document.service;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.examprep.api.dto.DocumentChunk;
import com.examprep.common.ProcessingFailedException;
import com.examprep.config.AsyncConfig;
import com.examprep.document.domain.Document;
import com.examprep.document.domain.DocumentChunkEntity;
import com.examprep.document.parser.ExtractionResult;
import com.examprep.document.parser.ExtractionService;
import com.examprep.document.parser.TextChunker;
import com.examprep.document.repository.DocumentChunkRepository;
import com.examprep.document.repository.DocumentRepository;
import com.examprep.document.storage.DocumentStorage;

/**
 * Extracts text from a stored document on a bounded background executor. Errors are persisted as a
 * safe student-facing reason; document contents are never written to the logs.
 *
 * <p>File extraction runs outside any transaction; each short write re-reads the document inside its
 * own transaction so a document deleted mid-extraction is never resurrected.
 */
@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);
    private static final int MIN_USEFUL_CHARS = 200;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentStorage storage;
    private final ExtractionService extractionService;
    private final TextChunker textChunker;
    private final TransactionTemplate transactionTemplate;

    public DocumentProcessingService(DocumentRepository documentRepository, DocumentChunkRepository chunkRepository,
                                     DocumentStorage storage, ExtractionService extractionService,
                                     TextChunker textChunker, PlatformTransactionManager transactionManager) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.storage = storage;
        this.extractionService = extractionService;
        this.textChunker = textChunker;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Async(AsyncConfig.DOCUMENT_EXECUTOR)
    public void process(UUID documentId) {
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            return;
        }
        markProcessing(documentId);
        try {
            Path file = storage.load(document.getStorageKey());
            ExtractionResult result = extractionService.extract(file, document.getFileExtension());
            List<DocumentChunk> chunks = textChunker.chunk(result.blocks());
            int characters = chunks.stream().mapToInt(chunk -> chunk.text().length()).sum();
            if (chunks.isEmpty() || characters < MIN_USEFUL_CHARS) {
                throw new ProcessingFailedException(noTextMessage(document.getFileExtension()));
            }
            int pageCount = result.pageCount() == null ? 0 : result.pageCount();
            transactionTemplate.executeWithoutResult(status -> {
                Document current = documentRepository.findById(documentId).orElse(null);
                if (current == null) {
                    // The document was deleted while it was being extracted; never resurrect it.
                    return;
                }
                chunkRepository.deleteAllForDocument(documentId);
                List<DocumentChunkEntity> entities = chunks.stream()
                        .map(chunk -> DocumentChunkEntity.of(documentId, chunk.refId(), chunk.ordinal(),
                                chunk.locationLabel(), chunk.text()))
                        .toList();
                chunkRepository.saveAll(entities);
                current.markReady(pageCount, characters);
                documentRepository.save(current);
            });
            log.info("Extracted document {} into {} chunks ({} chars)", documentId, chunks.size(), characters);
        } catch (ProcessingFailedException exception) {
            log.warn("Extraction failed for document {}: {}", documentId, exception.getMessage());
            fail(documentId, exception.getMessage());
        } catch (Exception exception) {
            log.warn("Extraction failed unexpectedly for document {}", documentId, exception);
            fail(documentId, "We could not process this file. It may be corrupted or password protected. "
                    + "Try re-exporting it as a PDF or DOCX and upload it again.");
        }
    }

    private void markProcessing(UUID documentId) {
        transactionTemplate.executeWithoutResult(status -> documentRepository.findById(documentId)
                .ifPresent(current -> {
                    current.markProcessing();
                    documentRepository.save(current);
                }));
    }

    private void fail(UUID documentId, String message) {
        transactionTemplate.executeWithoutResult(status -> documentRepository.findById(documentId)
                .ifPresent(current -> {
                    current.markFailed(message);
                    documentRepository.save(current);
                }));
    }

    private String noTextMessage(String extension) {
        if ("pdf".equalsIgnoreCase(extension)) {
            return "No readable text was found in this PDF. It is probably a scanned document or a set "
                    + "of images, and ExamPrep does not perform OCR yet. Upload a text-based PDF or the "
                    + "original notes.";
        }
        return "No readable text was found in this document. It may contain only images or be empty. "
                + "Try exporting it as a text-based PDF or DOCX.";
    }
}
