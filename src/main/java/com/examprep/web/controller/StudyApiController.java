package com.examprep.web.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.examprep.api.CurrentUserService;
import com.examprep.api.StudyPackApplicationService;
import com.examprep.api.dto.PracticeAnswerDto;
import com.examprep.api.dto.StudyPackStatusDto;
import com.examprep.api.dto.TutorAnswerDto;
import com.examprep.api.dto.TutorQuestionDto;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/study")
public class StudyApiController {

    private final CurrentUserService currentUserService;
    private final StudyPackApplicationService studyPackService;

    public StudyApiController(CurrentUserService currentUserService, StudyPackApplicationService studyPackService) {
        this.currentUserService = currentUserService;
        this.studyPackService = studyPackService;
    }

    @GetMapping("/{studyPackId}/status")
    public StudyPackStatusDto status(@PathVariable UUID studyPackId) {
        return studyPackService.getStatus(studyPackId, currentUserService.requireCurrentUserId());
    }

    @PostMapping("/{studyPackId}/flashcards/review")
    public ResponseEntity<Void> reviewFlashcards(@PathVariable UUID studyPackId,
                                                 @RequestBody Map<String, Integer> body) {
        UUID ownerId = currentUserService.requireCurrentUserId();
        int reviewed = body.getOrDefault("reviewed", 0);
        int total = body.getOrDefault("total", 0);
        studyPackService.recordFlashcardReview(studyPackId, ownerId, reviewed, total);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{studyPackId}/practice/{questionId}/answer")
    public PracticeAnswerDto practiceAnswer(@PathVariable UUID studyPackId, @PathVariable UUID questionId) {
        return studyPackService.revealPracticeAnswer(studyPackId, questionId,
                currentUserService.requireCurrentUserId());
    }

    @PostMapping("/{studyPackId}/tutor")
    public TutorAnswerDto askTutor(@PathVariable UUID studyPackId,
                                   @Valid @RequestBody TutorQuestionDto question) {
        return studyPackService.askTutor(studyPackId, currentUserService.requireCurrentUserId(), question);
    }
}
