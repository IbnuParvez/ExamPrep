package com.examprep.web.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.examprep.api.CurrentUserService;
import com.examprep.api.DocumentApplicationService;
import com.examprep.api.dto.DocumentStatusDto;

@RestController
@RequestMapping("/api/documents")
public class DocumentApiController {

    private final CurrentUserService currentUserService;
    private final DocumentApplicationService documentService;

    public DocumentApiController(CurrentUserService currentUserService, DocumentApplicationService documentService) {
        this.currentUserService = currentUserService;
        this.documentService = documentService;
    }

    @GetMapping("/{documentId}/status")
    public DocumentStatusDto status(@PathVariable UUID documentId) {
        return documentService.getStatus(documentId, currentUserService.requireCurrentUserId());
    }
}
