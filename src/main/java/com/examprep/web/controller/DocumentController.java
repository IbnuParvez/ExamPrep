package com.examprep.web.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

import com.examprep.api.CurrentUserService;
import com.examprep.api.DocumentApplicationService;
import com.examprep.api.StudyPackApplicationService;
import com.examprep.api.dto.DocumentStatusDto;
import com.examprep.api.dto.DocumentUploadResult;
import com.examprep.api.dto.StudyPackCreationResult;
import com.examprep.config.StorageProperties;
import com.examprep.web.form.StudyPreferencesForm;

@Controller
@RequestMapping("/documents")
public class DocumentController {

    private final CurrentUserService currentUserService;
    private final DocumentApplicationService documentService;
    private final StudyPackApplicationService studyPackService;
    private final StorageProperties storageProperties;

    public DocumentController(CurrentUserService currentUserService, DocumentApplicationService documentService,
                              StudyPackApplicationService studyPackService, StorageProperties storageProperties) {
        this.currentUserService = currentUserService;
        this.documentService = documentService;
        this.studyPackService = studyPackService;
        this.storageProperties = storageProperties;
    }

    @GetMapping
    public String list(Model model) {
        UUID ownerId = currentUserService.requireCurrentUserId();
        model.addAttribute("activeNav", "documents");
        model.addAttribute("documents", documentService.listDocuments(ownerId));
        return "documents/list";
    }

    @GetMapping("/upload")
    public String uploadForm(Model model) {
        model.addAttribute("activeNav", "documents");
        model.addAttribute("maxSizeMb", storageProperties.getMaxFileSizeMb());
        model.addAttribute("formats", List.of("PDF", "DOCX", "DOC", "PPTX", "PPT", "TXT", "Markdown", "RTF",
                "ODT", "ODP"));
        return "documents/upload";
    }

    @PostMapping
    public Object upload(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes,
                         HttpServletRequest request) {
        UUID ownerId = currentUserService.requireCurrentUserId();
        DocumentUploadResult result = documentService.upload(file, ownerId);
        if (isAjaxRequest(request)) {
            // Upload progress is tracked in the browser; JSON lets it navigate to the new document.
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                    .body(result);
        }
        redirectAttributes.addFlashAttribute("uploaded", true);
        return "redirect:/documents/" + result.documentId();
    }

    private boolean isAjaxRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("application/json");
    }

    @GetMapping("/{documentId}")
    public String detail(@PathVariable UUID documentId, Model model) {
        UUID ownerId = currentUserService.requireCurrentUserId();
        DocumentStatusDto document = documentService.getStatus(documentId, ownerId);
        model.addAttribute("activeNav", "documents");
        model.addAttribute("document", document);
        model.addAttribute("studyPacks", studyPackService.listStudyPacks(ownerId).stream()
                .filter(pack -> pack.documentId().equals(documentId))
                .toList());
        return "documents/detail";
    }

    @PostMapping("/{documentId}/retry")
    public String retry(@PathVariable UUID documentId, RedirectAttributes redirectAttributes) {
        UUID ownerId = currentUserService.requireCurrentUserId();
        documentService.retryExtraction(documentId, ownerId);
        redirectAttributes.addFlashAttribute("notice", "Extraction has been queued again.");
        return "redirect:/documents/" + documentId;
    }

    @PostMapping("/{documentId}/delete")
    public String delete(@PathVariable UUID documentId, RedirectAttributes redirectAttributes) {
        UUID ownerId = currentUserService.requireCurrentUserId();
        documentService.delete(documentId, ownerId);
        redirectAttributes.addFlashAttribute("notice", "Document and its study packages were deleted.");
        return "redirect:/documents";
    }

    @GetMapping("/{documentId}/study")
    public String studyForm(@PathVariable UUID documentId, Model model) {
        UUID ownerId = currentUserService.requireCurrentUserId();
        DocumentStatusDto document = documentService.getStatus(documentId, ownerId);
        if (!document.ready()) {
            model.addAttribute("document", document);
            return "documents/not-ready";
        }
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new StudyPreferencesForm());
        }
        model.addAttribute("activeNav", "documents");
        model.addAttribute("document", document);
        return "study/new";
    }

    @PostMapping("/{documentId}/study")
    public String createStudyPack(@PathVariable UUID documentId,
                                  @ModelAttribute("form") StudyPreferencesForm form,
                                  RedirectAttributes redirectAttributes) {
        UUID ownerId = currentUserService.requireCurrentUserId();
        StudyPackCreationResult result = studyPackService.create(documentId, ownerId, form.toPreferences());
        redirectAttributes.addFlashAttribute("created", true);
        return "redirect:/study/" + result.studyPackId();
    }
}
