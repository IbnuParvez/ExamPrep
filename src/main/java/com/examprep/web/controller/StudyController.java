package com.examprep.web.controller;

import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.examprep.api.CurrentUserService;
import com.examprep.api.StudyPackApplicationService;
import com.examprep.api.dto.StudyPackDto;
import com.examprep.api.model.StudyPackStatus;

@Controller
@RequestMapping("/study")
public class StudyController {

    private final CurrentUserService currentUserService;
    private final StudyPackApplicationService studyPackService;

    public StudyController(CurrentUserService currentUserService, StudyPackApplicationService studyPackService) {
        this.currentUserService = currentUserService;
        this.studyPackService = studyPackService;
    }

    @GetMapping("/{studyPackId}")
    public String packagePage(@PathVariable UUID studyPackId,
                              @RequestParam(name = "tab", defaultValue = "overview") String tab,
                              Model model) {
        UUID ownerId = currentUserService.requireCurrentUserId();
        StudyPackDto studyPack = studyPackService.getStudyPack(studyPackId, ownerId);
        model.addAttribute("activeNav", "study");
        model.addAttribute("pack", studyPack);
        if (studyPack.status() != StudyPackStatus.READY) {
            model.addAttribute("status", studyPackService.getStatus(studyPackId, ownerId));
            return "study/generating";
        }
        model.addAttribute("tab", allowedTab(tab));
        return "study/package";
    }

    @GetMapping("/{studyPackId}/flashcards")
    public String flashcards(@PathVariable UUID studyPackId, Model model) {
        UUID ownerId = currentUserService.requireCurrentUserId();
        StudyPackDto studyPack = requireReady(studyPackId, ownerId, model);
        if (studyPack == null) {
            return "study/generating";
        }
        model.addAttribute("activeNav", "study");
        model.addAttribute("pack", studyPack);
        return "study/flashcards";
    }

    @GetMapping("/{studyPackId}/practice")
    public String practice(@PathVariable UUID studyPackId, Model model) {
        UUID ownerId = currentUserService.requireCurrentUserId();
        StudyPackDto studyPack = requireReady(studyPackId, ownerId, model);
        if (studyPack == null) {
            return "study/generating";
        }
        model.addAttribute("activeNav", "study");
        model.addAttribute("pack", studyPack);
        return "study/practice";
    }

    @GetMapping("/{studyPackId}/tutor")
    public String tutor(@PathVariable UUID studyPackId, Model model) {
        UUID ownerId = currentUserService.requireCurrentUserId();
        StudyPackDto studyPack = requireReady(studyPackId, ownerId, model);
        if (studyPack == null) {
            return "study/generating";
        }
        model.addAttribute("activeNav", "study");
        model.addAttribute("pack", studyPack);
        model.addAttribute("messages", studyPackService.tutorHistory(studyPackId, ownerId));
        return "study/tutor";
    }

    @PostMapping("/{studyPackId}/delete")
    public String delete(@PathVariable UUID studyPackId, RedirectAttributes redirectAttributes) {
        UUID ownerId = currentUserService.requireCurrentUserId();
        studyPackService.delete(studyPackId, ownerId);
        redirectAttributes.addFlashAttribute("notice", "Study package deleted.");
        return "redirect:/dashboard";
    }

    private StudyPackDto requireReady(UUID studyPackId, UUID ownerId, Model model) {
        StudyPackDto studyPack = studyPackService.getStudyPack(studyPackId, ownerId);
        model.addAttribute("pack", studyPack);
        if (studyPack.status() != StudyPackStatus.READY) {
            model.addAttribute("status", studyPackService.getStatus(studyPackId, ownerId));
            return null;
        }
        return studyPack;
    }

    private String allowedTab(String tab) {
        return switch (tab) {
            case "topics", "notes", "definitions", "formulas" -> tab;
            default -> "overview";
        };
    }
}
