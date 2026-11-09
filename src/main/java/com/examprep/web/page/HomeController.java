package com.examprep.web.page;

import com.examprep.api.document.DocumentApplicationService;
import com.examprep.api.study.StudyPackApplicationService;
import com.examprep.config.AppProperties;
import com.examprep.document.domain.DocumentFormat;
import com.examprep.security.CurrentUserService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final StudyPackApplicationService studyPackService;
    private final DocumentApplicationService documentService;
    private final CurrentUserService currentUserService;
    private final AppProperties properties;

    public HomeController(StudyPackApplicationService studyPackService,
                          DocumentApplicationService documentService,
                          CurrentUserService currentUserService,
                          AppProperties properties) {
        this.studyPackService = studyPackService;
        this.documentService = documentService;
        this.currentUserService = currentUserService;
        this.properties = properties;
    }

    @GetMapping("/")
    public String landing(Model model) {
        model.addAttribute("supportedFormats", List.of(DocumentFormat.values()).stream()
                .map(DocumentFormat::label)
                .distinct()
                .toList());
        model.addAttribute("maxUploadMb", properties.upload().maxSizeMb());
        return "index";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        UUID studentId = currentUserService.requireCurrentUserId();
        model.addAttribute("displayName", currentUserService.currentDisplayName());
        model.addAttribute("progress", studyPackService.getProgress(studentId));
        model.addAttribute("documents", documentService.listDocuments(studentId).stream().limit(4).toList());
        model.addAttribute("maxUploadMb", properties.upload().maxSizeMb());
        return "dashboard/index";
    }
}
