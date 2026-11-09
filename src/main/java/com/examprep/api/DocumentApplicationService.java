package com.examprep.api;

import java.util.UUID;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.examprep.api.dto.DocumentContext;
import com.examprep.api.dto.DocumentStatusDto;
import com.examprep.api.dto.DocumentSummaryDto;
import com.examprep.api.dto.DocumentUploadResult;

/** Application-level contract for document ingestion. All methods enforce ownership. */
public interface DocumentApplicationService {

    DocumentUploadResult upload(MultipartFile file, UUID ownerId);

    DocumentStatusDto getStatus(UUID documentId, UUID ownerId);

    List<DocumentSummaryDto> listDocuments(UUID ownerId);

    DocumentContext getContext(UUID documentId, UUID ownerId);

    void retryExtraction(UUID documentId, UUID ownerId);

    void delete(UUID documentId, UUID ownerId);
}
