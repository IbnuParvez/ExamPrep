package com.examprep.web.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.examprep.api.CurrentUserService;
import com.examprep.api.DocumentApplicationService;
import com.examprep.api.StudyPackApplicationService;
import com.examprep.api.dto.DocumentSummaryDto;
import com.examprep.api.dto.QuizAttemptSummaryDto;
import com.examprep.api.dto.StudyPackSummaryDto;

@Controller
public class DashboardController {

    private final CurrentUserService currentUserService;
    private final DocumentApplicationService documentService;
    private final StudyPackApplicationService studyPackService;

    public DashboardController(CurrentUserService currentUserService, DocumentApplicationService documentService,
                               StudyPackApplicationService studyPackService) {
        this.currentUserService = currentUserService;
        this.documentService = documentService;
        this.studyPackService = studyPackService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        UUID ownerId = currentUserService.requireCurrentUserId();
        List<DocumentSummaryDto> documents = documentService.listDocuments(ownerId);
        List<StudyPackSummaryDto> studyPacks = studyPackService.listStudyPacks(ownerId);
        List<QuizAttemptSummaryDto> attempts = studyPackService.recentAttempts(ownerId, 10);

        model.addAttribute("activeNav", "dashboard");
        model.addAttribute("documents", documents.stream().limit(4).toList());
        model.addAttribute("documentCount", documents.size());
        model.addAttribute("studyPacks", studyPacks.stream().limit(4).toList());
        model.addAttribute("studyPackCount", studyPacks.size());
        model.addAttribute("attempts", attempts.stream().limit(4).toList());
        model.addAttribute("attemptCount", attempts.size());
        model.addAttribute("bestScore", attempts.stream().mapToInt(QuizAttemptSummaryDto::percentage).max().orElse(0));
        model.addAttribute("averageScore", attempts.isEmpty() ? 0
                : (int) Math.round(attempts.stream().mapToInt(QuizAttemptSummaryDto::percentage).average().orElse(0)));
        return "dashboard/index";
    }
}
