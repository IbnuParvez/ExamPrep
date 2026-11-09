package com.examprep.document.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.examprep.api.DocumentApplicationService;
import com.examprep.api.dto.DocumentContext;
import com.examprep.api.dto.DocumentStatusDto;
import com.examprep.api.dto.DocumentSummaryDto;
import com.examprep.api.dto.DocumentUploadResult;
import com.examprep.api.model.DocumentStatus;
import com.examprep.common.NotFoundException;
import com.examprep.common.ProcessingFailedException;
import com.examprep.common.ValidationException;
import com.examprep.document.domain.Document;
import com.examprep.document.domain.DocumentChunkEntity;
import com.examprep.document.repository.DocumentChunkRepository;
import com.examprep.document.repository.DocumentRepository;
import com.examprep.document.storage.DocumentStorage;

@Service
public class DocumentApplicationServiceImpl implements DocumentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentApplicationServiceImpl.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentStorage storage;
    private final DocumentValidator validator;
    private final DocumentProcessingService processingService;

    public DocumentApplicationServiceImpl(DocumentRepository documentRepository,
                                          DocumentChunkRepository chunkRepository,
                                          DocumentStorage storage,
                                          DocumentValidator validator,
                                          DocumentProcessingService processingService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.storage = storage;
        this.validator = validator;
        this.processingService = processingService;
    }

    @Override
    public DocumentUploadResult upload(MultipartFile file, UUID ownerId) {
        ValidatedUpload validated = validator.validate(file);
        String storageKey;
        try (InputStream inputStream = file.getInputStream()) {
            storageKey = storage.store(inputStream, validated.extension());
        } catch (IOException exception) {
            throw new ProcessingFailedException("The file could not be saved. Please try again.", exception);
        }

        Document document = Document.uploaded(ownerId, validated.originalFilename(), validated.contentType(),
                validated.extension(), storageKey, validated.sizeBytes());
        documentRepository.saveAndFlush(document);
        schedule(document);
        return new DocumentUploadResult(document.getId(), document.getOriginalFilename(), document.getStatus());
    }

    @Override
    public DocumentStatusDto getStatus(UUID documentId, UUID ownerId) {
        Document document = requireOwned(documentId, ownerId);
        return new DocumentStatusDto(document.getId(), document.getOriginalFilename(), document.getContentType(),
                document.getStatus(), safeFailure(document), (int) chunkRepository.countByDocumentId(documentId),
                document.getPageCount(), document.getCreatedAt());
    }

    @Override
    public List<DocumentSummaryDto> listDocuments(UUID ownerId) {
        return documentRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public DocumentContext getContext(UUID documentId, UUID ownerId) {
        Document document = requireOwned(documentId, ownerId);
        if (document.getStatus() != DocumentStatus.READY) {
            throw new ValidationException("This document has not finished processing yet.");
        }
        List<DocumentChunkEntity> entities = chunkRepository.findByDocumentIdOrderByOrdinalAsc(documentId);
        List<com.examprep.api.dto.DocumentChunk> chunks = entities.stream()
                .map(entity -> new com.examprep.api.dto.DocumentChunk(entity.getRefId(), entity.getOrdinal(),
                        entity.getLocationLabel(), entity.getContent()))
                .toList();
        return new DocumentContext(document.getId(), document.getOriginalFilename(), document.displayTitle(), chunks);
    }

    @Override
    public void retryExtraction(UUID documentId, UUID ownerId) {
        Document document = requireOwned(documentId, ownerId);
        if (document.getStatus() == DocumentStatus.PROCESSING) {
            throw new ValidationException("This document is already being processed.");
        }
        document.resetForRetry();
        documentRepository.saveAndFlush(document);
        schedule(document);
    }

    @Override
    public void delete(UUID documentId, UUID ownerId) {
        Document document = requireOwned(documentId, ownerId);
        storage.delete(document.getStorageKey());
        documentRepository.delete(document);
    }

    private void schedule(Document document) {
        try {
            processingService.process(document.getId());
        } catch (TaskRejectedException exception) {
            log.warn("Document processing queue is full; document {} was not scheduled", document.getId());
            document.markFailed("The processing queue is busy. Use Retry extraction in a moment.");
            documentRepository.save(document);
            throw new ValidationException("The processing queue is busy right now. Please retry in a moment.");
        }
    }

    private Document requireOwned(UUID documentId, UUID ownerId) {
        return documentRepository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new NotFoundException("That document could not be found."));
    }

    private DocumentSummaryDto toSummary(Document document) {
        int chunks = (int) chunkRepository.countByDocumentId(document.getId());
        return new DocumentSummaryDto(document.getId(), document.getOriginalFilename(), document.getContentType(),
                document.getFileSize(), document.getStatus(), document.getCreatedAt(), chunks);
    }

    /** Failure text is written by us, so it is safe to display; still guard against nulls. */
    private String safeFailure(Document document) {
        return document.getStatus() == DocumentStatus.FAILED ? document.getFailureReason() : null;
    }
}
