package com.examprep.web.page;

import com.examprep.api.document.DocumentApplicationService;
import com.examprep.api.study.QuizResultDto;
import com.examprep.api.study.StudyPackApplicationService;
import com.examprep.api.study.StudyPackDto;
import com.examprep.api.study.StudyPackStatus;
import com.examprep.api.study.StudyPackStatusDto;
import com.examprep.api.study.TutorMessageDto;
import com.examprep.security.CurrentUserService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/study")
public class StudyPackController {

    private final StudyPackApplicationService studyPackService;
    private final DocumentApplicationService documentService;
    private final CurrentUserService currentUserService;

    public StudyPackController(StudyPackApplicationService studyPackService,
                               DocumentApplicationService documentService,
                               CurrentUserService currentUserService) {
        this.studyPackService = studyPackService;
        this.documentService = documentService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public String list(Model model) {
        UUID studentId = currentUserService.requireCurrentUserId();
        model.addAttribute("studyPacks", studyPackService.listStudyPacks(studentId));
        return "study/index";
    }

    @GetMapping("/{studyPackId}")
    public String overview(@PathVariable UUID studyPackId, Model model) {
        UUID studentId = currentUserService.requireCurrentUserId();
        StudyPackStatusDto status = studyPackService.getStatus(studyPackId, studentId);
        if (status.status() != StudyPackStatus.READY) {
            model.addAttribute("status", status);
            model.addAttribute("studyPackId", studyPackId);
            return "study/status";
        }
        StudyPackDto pack = studyPackService.getStudyPack(studyPackId, studentId);
        addCommon(model, pack, "overview");
        return "study/overview";
    }

    @GetMapping("/{studyPackId}/topics")
    public String topics(@PathVariable UUID studyPackId, Model model) {
        StudyPackDto pack = readyPack(studyPackId);
        addCommon(model, pack, "topics");
        return "study/topics";
    }

    @GetMapping("/{studyPackId}/notes")
    public String notes(@PathVariable UUID studyPackId, Model model) {
        StudyPackDto pack = readyPack(studyPackId);
        addCommon(model, pack, "notes");
        return "study/notes";
    }

    @GetMapping("/{studyPackId}/definitions")
    public String definitions(@PathVariable UUID studyPackId, Model model) {
        StudyPackDto pack = readyPack(studyPackId);
        addCommon(model, pack, "definitions");
        return "study/definitions";
    }

    @GetMapping("/{studyPackId}/formulas")
    public String formulas(@PathVariable UUID studyPackId, Model model) {
        StudyPackDto pack = readyPack(studyPackId);
        addCommon(model, pack, "formulas");
        return "study/formulas";
    }

    @GetMapping("/{studyPackId}/flashcards")
    public String flashcards(@PathVariable UUID studyPackId, Model model) {
        StudyPackDto pack = readyPack(studyPackId);
        addCommon(model, pack, "flashcards");
        model.addAttribute("flashcards", pack.flashcards());
        return "study/flashcards";
    }

    @GetMapping("/{studyPackId}/practice")
    public String practice(@PathVariable UUID studyPackId, Model model) {
        StudyPackDto pack = readyPack(studyPackId);
        addCommon(model, pack, "practice");
        model.addAttribute("questions", pack.practiceQuestions());
        return "study/practice";
    }

    @GetMapping("/{studyPackId}/exam")
    public String exam(@PathVariable UUID studyPackId, Model model) {
        UUID studentId = currentUserService.requireCurrentUserId();
        StudyPackDto pack = studyPackService.getStudyPack(studyPackId, studentId);
        addCommon(model, pack, "exam");
        model.addAttribute("questions", studyPackService.getExamQuestions(studyPackId, studentId));
        model.addAttribute("durationMinutes", pack.examDurationMinutes());
        model.addAttribute("examSeconds", pack.examDurationMinutes() * 60);
        return "study/exam";
    }

    @GetMapping("/{studyPackId}/tutor")
    public String tutor(@PathVariable UUID studyPackId, Model model) {
        UUID studentId = currentUserService.requireCurrentUserId();
        StudyPackDto pack = studyPackService.getStudyPack(studyPackId, studentId);
        addCommon(model, pack, "tutor");
        List<TutorMessageDto> conversation = studyPackService.getTutorConversation(studyPackId, studentId);
        model.addAttribute("conversation", conversation);
        return "study/tutor";
    }

    @GetMapping("/{studyPackId}/results/{attemptId}")
    public String results(@PathVariable UUID studyPackId, @PathVariable UUID attemptId, Model model) {
        UUID studentId = currentUserService.requireCurrentUserId();
        QuizResultDto result = studyPackService.getAttempt(studyPackId, studentId, attemptId);
        StudyPackDto pack = studyPackService.getStudyPack(studyPackId, studentId);
        addCommon(model, pack, "exam");
        model.addAttribute("result", result);
        return "study/results";
    }

    @PostMapping("/{studyPackId}/delete")
    public String delete(@PathVariable UUID studyPackId, RedirectAttributes redirectAttributes) {
        UUID studentId = currentUserService.requireCurrentUserId();
        studyPackService.delete(studyPackId, studentId);
        redirectAttributes.addFlashAttribute("studyPackDeleted", true);
        return "redirect:/study";
    }

    private StudyPackDto readyPack(UUID studyPackId) {
        UUID studentId = currentUserService.requireCurrentUserId();
        return studyPackService.getStudyPack(studyPackId, studentId);
    }

    private void addCommon(Model model, StudyPackDto pack, String activeSection) {
        model.addAttribute("pack", pack);
        model.addAttribute("activeSection", activeSection);
        if (pack.documentId() != null) {
            try {
                model.addAttribute("document", documentService.getDocument(pack.documentId(),
                        currentUserService.requireCurrentUserId()));
            } catch (RuntimeException ignored) {
                // The document may have been removed; the study pack remains readable.
            }
        }
    }
}
