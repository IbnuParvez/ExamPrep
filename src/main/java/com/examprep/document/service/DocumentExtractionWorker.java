package com.examprep.document.service;

import com.examprep.config.AppProperties;
import com.examprep.document.domain.Document;
import com.examprep.document.domain.DocumentFormat;
import com.examprep.document.parser.DocumentExtractionException;
import com.examprep.document.parser.DocumentParserRegistry;
import com.examprep.document.parser.ExtractedDocument;
import com.examprep.document.parser.ExtractedSection;
import com.examprep.document.repository.DocumentRepository;
import com.examprep.document.storage.DocumentStorageService;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Extracts a stored document in the background and records a terminal state. Extraction never
 * runs inside an HTTP request, so large files cannot time out a browser session.
 */
@Service
public class DocumentExtractionWorker {

    private static final Logger log = LoggerFactory.getLogger(DocumentExtractionWorker.class);

    private final DocumentRepository documentRepository;
    private final DocumentStateWriter stateWriter;
    private final DocumentStorageService storageService;
    private final DocumentParserRegistry parserRegistry;
    private final DocumentChunker chunker;
    private final AppProperties properties;

    public DocumentExtractionWorker(DocumentRepository documentRepository,
                                    DocumentStateWriter stateWriter,
                                    DocumentStorageService storageService,
                                    DocumentParserRegistry parserRegistry,
                                    DocumentChunker chunker,
                                    AppProperties properties) {
        this.documentRepository = documentRepository;
        this.stateWriter = stateWriter;
        this.storageService = storageService;
        this.parserRegistry = parserRegistry;
        this.chunker = chunker;
        this.properties = properties;
    }

    @Async("documentTaskExecutor")
    public void process(UUID documentId) {
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null || document.getStatus().isTerminal()) {
            return;
        }
        stateWriter.markProcessing(documentId);

        Document processing = documentRepository.findById(documentId).orElse(null);
        if (processing == null) {
            return;
        }
        try {
            DocumentFormat format = DocumentFormat.fromExtension(processing.getFileExtension())
                    .orElseThrow(() -> new DocumentExtractionException(
                            "The stored file type is no longer supported."));
            Path file = storageService.resolve(processing.getStorageKey());
            ExtractedDocument extracted = parserRegistry.extract(file, format);

            int characters = countCharacters(extracted);
            int minimum = properties.processing().minimumMeaningfulCharacters();
            if (characters < minimum) {
                stateWriter.markFailed(documentId,
                        "No readable text could be extracted from this document. If it is a scanned PDF, "
                                + "it contains images of text rather than text itself, which is not supported yet. "
                                + "Upload a text-based document instead.");
                return;
            }
            int maximum = properties.processing().maxExtractedCharacters();
            if (characters > maximum) {
                stateWriter.markFailed(documentId,
                        "This document contains more text than the application can process at once. "
                                + "Split it into smaller documents and upload them separately.");
                return;
            }
            List<ChunkDraft> chunks = chunker.chunk(extracted);
            if (chunks.isEmpty()) {
                stateWriter.markFailed(documentId,
                        "No readable text could be extracted from this document.");
                return;
            }
            stateWriter.markReady(documentId, extracted.pageCount(), characters, chunks);
            log.info("Extracted document {} into {} chunks ({} characters)", documentId, chunks.size(), characters);
        } catch (DocumentExtractionException expected) {
            stateWriter.markFailed(documentId, expected.getMessage());
        } catch (Exception unexpected) {
            log.error("Unexpected extraction failure for document {}", documentId, unexpected);
            stateWriter.markFailed(documentId,
                    "The document could not be processed because of an unexpected error.");
        }
    }

    private static int countCharacters(ExtractedDocument extracted) {
        int total = 0;
        for (ExtractedSection section : extracted.sections()) {
            total += section.text() == null ? 0 : section.text().length();
        }
        return total;
    }
}
