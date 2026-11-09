package com.examprep.document.service;

import com.examprep.api.document.DocumentApplicationService;
import com.examprep.api.document.DocumentChunkDto;
import com.examprep.api.document.DocumentContext;
import com.examprep.api.document.DocumentProcessingStatus;
import com.examprep.api.document.DocumentStatusDto;
import com.examprep.api.document.DocumentSummaryDto;
import com.examprep.api.document.DocumentUploadResult;
import com.examprep.common.ResourceNotFoundException;
import com.examprep.common.ValidationException;
import com.examprep.document.domain.Document;
import com.examprep.document.domain.DocumentChunk;
import com.examprep.document.repository.DocumentChunkRepository;
import com.examprep.document.repository.DocumentRepository;
import com.examprep.document.storage.DocumentStorageService;
import com.examprep.document.storage.StoredFile;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentApplicationServiceImpl implements DocumentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentApplicationServiceImpl.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentValidationService validationService;
    private final DocumentStorageService storageService;
    private final DocumentExtractionWorker extractionWorker;
    private final DocumentStateWriter stateWriter;

    public DocumentApplicationServiceImpl(DocumentRepository documentRepository,
                                          DocumentChunkRepository chunkRepository,
                                          DocumentValidationService validationService,
                                          DocumentStorageService storageService,
                                          DocumentExtractionWorker extractionWorker,
                                          DocumentStateWriter stateWriter) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.validationService = validationService;
        this.storageService = storageService;
        this.extractionWorker = extractionWorker;
        this.stateWriter = stateWriter;
    }

    @Override
    public DocumentUploadResult upload(MultipartFile file, UUID ownerId) {
        ValidatedUpload validated = validationService.validate(file);
        StoredFile stored = storageService.store(file, ownerId, validated.format());

        Document document = Document.uploaded(ownerId, validated.originalFilename(),
                validated.detectedContentType(), validated.format(), stored.storageKey(), stored.sizeBytes());
        documentRepository.save(document);

        dispatchExtraction(document.getId());

        Document refreshed = documentRepository.findById(document.getId()).orElse(document);
        return new DocumentUploadResult(refreshed.getId(), refreshed.getOriginalFilename(),
                refreshed.getStatus(), refreshed.getFileSizeBytes());
    }

    private void dispatchExtraction(UUID documentId) {
        try {
            extractionWorker.process(documentId);
        } catch (RejectedExecutionException rejected) {
            log.warn("Extraction queue is full; document {} was not queued", documentId);
            stateWriter.markFailed(documentId,
                    "The document processing queue is currently full. Please try uploading the file again in a moment.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentStatusDto getStatus(UUID documentId, UUID ownerId) {
        Document document = requireOwned(documentId, ownerId);
        int chunkCount = (int) chunkRepository.countByDocumentId(document.getId());
        return new DocumentStatusDto(document.getId(), document.getOriginalFilename(),
                document.getContentType(), document.getFileSizeBytes(), document.getStatus(),
                chunkCount, document.getPageCount(), document.getCharacterCount(),
                document.getFailureReason(), document.getCreatedAt(), document.getUpdatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentSummaryDto getDocument(UUID documentId, UUID ownerId) {
        return toSummary(requireOwned(documentId, ownerId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentSummaryDto> listDocuments(UUID ownerId) {
        return documentRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentContext getContext(UUID documentId, UUID ownerId) {
        Document document = requireOwned(documentId, ownerId);
        if (document.getStatus() != DocumentProcessingStatus.READY) {
            throw new ValidationException("This document has not finished processing yet.");
        }
        List<DocumentChunkDto> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(document.getId())
                .stream()
                .map(DocumentApplicationServiceImpl::toChunkDto)
                .toList();
        if (chunks.isEmpty()) {
            throw new ValidationException("This document does not contain any extracted text yet.");
        }
        return new DocumentContext(document.getId(), document.getOwnerId(), document.getOriginalFilename(), chunks);
    }

    @Override
    @Transactional
    public void delete(UUID documentId, UUID ownerId) {
        Document document = requireOwned(documentId, ownerId);
        String storageKey = document.getStorageKey();
        documentRepository.delete(document);
        documentRepository.flush();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storageService.deleteQuietly(storageKey);
                }
            });
        } else {
            storageService.deleteQuietly(storageKey);
        }
    }

    private Document requireOwned(UUID documentId, UUID ownerId) {
        return documentRepository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Document", documentId));
    }

    private DocumentSummaryDto toSummary(Document document) {
        return new DocumentSummaryDto(document.getId(), document.getOriginalFilename(), document.getContentType(),
                document.getFileSizeBytes(), document.getStatus(), document.getPageCount(),
                document.getFailureReason(), document.getCreatedAt(), document.getUpdatedAt());
    }

    private static DocumentChunkDto toChunkDto(DocumentChunk chunk) {
        return new DocumentChunkDto(chunk.getReferenceId(), chunk.getLocationLabel(), chunk.getHeading(),
                chunk.getContent(), chunk.getChunkIndex());
    }
}
