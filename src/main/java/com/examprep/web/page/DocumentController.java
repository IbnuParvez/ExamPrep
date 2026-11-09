package com.examprep.web.page;

import com.examprep.api.document.DocumentApplicationService;
import com.examprep.api.document.DocumentProcessingStatus;
import com.examprep.api.document.DocumentStatusDto;
import com.examprep.api.document.DocumentUploadResult;
import com.examprep.api.study.StudyPackApplicationService;
import com.examprep.api.study.StudyPackCreationResult;
import com.examprep.api.study.StudyPreferences;
import com.examprep.common.ValidationException;
import com.examprep.config.AppProperties;
import com.examprep.document.domain.DocumentFormat;
import com.examprep.security.CurrentUserService;
import com.examprep.web.page.form.StudyOptionsForm;
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

@Controller
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentApplicationService documentService;
    private final StudyPackApplicationService studyPackService;
    private final CurrentUserService currentUserService;
    private final AppProperties properties;

    public DocumentController(DocumentApplicationService documentService,
                              StudyPackApplicationService studyPackService,
                              CurrentUserService currentUserService,
                              AppProperties properties) {
        this.documentService = documentService;
        this.studyPackService = studyPackService;
        this.currentUserService = currentUserService;
        this.properties = properties;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String uploadError, Model model) {
        UUID studentId = currentUserService.requireCurrentUserId();
        model.addAttribute("documents", documentService.listDocuments(studentId));
        model.addAttribute("maxUploadMb", properties.upload().maxSizeMb());
        model.addAttribute("supportedExtensions", DocumentFormat.supportedExtensions().stream()
                .sorted()
                .map(extension -> "." + extension)
                .toList());
        model.addAttribute("supportedExtensionsCsv", DocumentFormat.supportedExtensions().stream()
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse(""));
        if (uploadError != null) {
            model.addAttribute("uploadError", "size".equals(uploadError)
                    ? "That file is larger than the " + properties.upload().maxSizeMb()
                    + " MB upload limit."
                    : "The upload could not be completed. Please try again.");
        }
        return "documents/index";
    }

    @PostMapping
    public String upload(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        UUID studentId = currentUserService.requireCurrentUserId();
        try {
            DocumentUploadResult result = documentService.upload(file, studentId);
            redirectAttributes.addFlashAttribute("uploaded", result.originalFilename());
            return "redirect:/documents/" + result.documentId();
        } catch (ValidationException invalid) {
            redirectAttributes.addFlashAttribute("uploadError", invalid.getMessage());
            return "redirect:/documents";
        }
    }

    @GetMapping("/{documentId}")
    public String detail(@PathVariable UUID documentId, Model model) {
        UUID studentId = currentUserService.requireCurrentUserId();
        DocumentStatusDto document = documentService.getStatus(documentId, studentId);
        model.addAttribute("document", document);
        StudyOptionsForm optionsForm = new StudyOptionsForm();
        optionsForm.setQuestionCount(properties.study().defaultQuestionCount());
        optionsForm.setFlashcardCount(properties.study().defaultFlashcardCount());
        optionsForm.setExamDurationMinutes(properties.study().defaultExamDurationMinutes());
        model.addAttribute("optionsForm", optionsForm);
        model.addAttribute("maxQuestionCount", properties.study().maxQuestionCount());
        model.addAttribute("maxFlashcardCount", properties.study().maxFlashcardCount());
        model.addAttribute("defaultQuestionCount", properties.study().defaultQuestionCount());
        model.addAttribute("defaultFlashcardCount", properties.study().defaultFlashcardCount());
        model.addAttribute("defaultExamDuration", properties.study().defaultExamDurationMinutes());
        model.addAttribute("processing", document.status() == DocumentProcessingStatus.UPLOADED
                || document.status() == DocumentProcessingStatus.PROCESSING);
        return "documents/detail";
    }

    @PostMapping("/{documentId}/delete")
    public String delete(@PathVariable UUID documentId, RedirectAttributes redirectAttributes) {
        UUID studentId = currentUserService.requireCurrentUserId();
        documentService.delete(documentId, studentId);
        redirectAttributes.addFlashAttribute("documentDeleted", true);
        return "redirect:/documents";
    }

    @PostMapping("/{documentId}/study-packs")
    public String createStudyPack(@PathVariable UUID documentId,
                                  @ModelAttribute("optionsForm") StudyOptionsForm form,
                                  RedirectAttributes redirectAttributes) {
        UUID studentId = currentUserService.requireCurrentUserId();
        StudyPreferences preferences = new StudyPreferences(form.getLanguage(), form.getAcademicLevel(),
                form.getExamDate(), form.getQuestionCount(), form.getFlashcardCount(),
                form.getExamDurationMinutes());
        StudyPackCreationResult result = studyPackService.create(documentId, studentId, preferences);
        return "redirect:/study/" + result.studyPackId();
    }
}
