package com.examprep.web.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.examprep.api.CurrentUserService;
import com.examprep.api.StudyPackApplicationService;
import com.examprep.api.dto.ExamSessionDto;
import com.examprep.api.dto.QuizResultDto;
import com.examprep.api.dto.QuizSubmissionDto;
import com.examprep.api.dto.StudyPackDto;
import com.examprep.api.model.StudyPackStatus;
import com.examprep.common.ValidationException;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class ExamController {

    private final CurrentUserService currentUserService;
    private final StudyPackApplicationService studyPackService;

    public ExamController(CurrentUserService currentUserService, StudyPackApplicationService studyPackService) {
        this.currentUserService = currentUserService;
        this.studyPackService = studyPackService;
    }

    @GetMapping("/study/{studyPackId}/exam")
    public String intro(@PathVariable UUID studyPackId, Model model) {
        UUID ownerId = currentUserService.requireCurrentUserId();
        StudyPackDto studyPack = studyPackService.getStudyPack(studyPackId, ownerId);
        model.addAttribute("activeNav", "study");
        model.addAttribute("pack", studyPack);
        if (studyPack.status() != StudyPackStatus.READY) {
            model.addAttribute("status", studyPackService.getStatus(studyPackId, ownerId));
            return "study/generating";
        }
        model.addAttribute("available", studyPack.examQuestionCount() >= 4);
        return "exam/intro";
    }

    @PostMapping("/study/{studyPackId}/exam/start")
    public String start(@PathVariable UUID studyPackId) {
        UUID ownerId = currentUserService.requireCurrentUserId();
        ExamSessionDto session = studyPackService.startExam(studyPackId, ownerId);
        return "redirect:/exam/" + session.attemptId();
    }

    @GetMapping("/exam/{attemptId}")
    public String take(@PathVariable UUID attemptId, Model model) {
        UUID ownerId = currentUserService.requireCurrentUserId();
        ExamSessionDto session;
        try {
            session = studyPackService.getExamSession(attemptId, ownerId);
        } catch (ValidationException exception) {
            return "redirect:/exam/" + attemptId + "/results";
        }
        model.addAttribute("exam", session);
        model.addAttribute("remainingSeconds", Math.max(0,
                session.durationSeconds() - session.timeTakenSeconds()));
        return "exam/take";
    }

    @PostMapping("/exam/{attemptId}/submit")
    public String submit(@PathVariable UUID attemptId,
                         @RequestParam(name = "timeTaken", defaultValue = "0") int timeTaken,
                         HttpServletRequest request, RedirectAttributes redirectAttributes) {
        UUID ownerId = currentUserService.requireCurrentUserId();
        ExamSessionDto session;
        try {
            session = studyPackService.getExamSession(attemptId, ownerId);
        } catch (ValidationException alreadySubmitted) {
            // A double-click, or the countdown racing a manual click: the attempt is already graded, so
            // send the student to the results instead of showing a 400 error page.
            return "redirect:/exam/" + attemptId + "/results";
        }
        List<QuizSubmissionDto.Answer> answers = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            String name = entry.getKey();
            if (!name.startsWith("q_") || entry.getValue().length == 0) {
                continue;
            }
            UUID questionId;
            try {
                questionId = UUID.fromString(name.substring(2));
            } catch (IllegalArgumentException exception) {
                continue;
            }
            String value = entry.getValue()[0];
            if (value == null || value.isBlank()) {
                continue;
            }
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                answers.add(new QuizSubmissionDto.Answer(questionId, null, Boolean.valueOf(value)));
            } else {
                try {
                    answers.add(new QuizSubmissionDto.Answer(questionId, Integer.valueOf(value), null));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed values rather than failing the whole submission.
                }
            }
        }
        studyPackService.submitExam(session.studyPackId(), ownerId,
                new QuizSubmissionDto(attemptId, timeTaken, true, answers));
        redirectAttributes.addFlashAttribute("submitted", true);
        return "redirect:/exam/" + attemptId + "/results";
    }

    @GetMapping("/exam/{attemptId}/results")
    public String results(@PathVariable UUID attemptId, Model model) {
        UUID ownerId = currentUserService.requireCurrentUserId();
        QuizResultDto result = studyPackService.getResult(attemptId, ownerId);
        model.addAttribute("activeNav", "study");
        model.addAttribute("result", result);
        model.addAttribute("exam", studyPackService.getStudyPack(result.studyPackId(), ownerId));
        return "exam/results";
    }
}
