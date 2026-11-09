package com.examprep.web.api;

import com.examprep.api.document.DocumentApplicationService;
import com.examprep.api.document.DocumentStatusDto;
import com.examprep.api.study.PracticeAnswerDto;
import com.examprep.api.study.QuizResultDto;
import com.examprep.api.study.QuizSubmissionDto;
import com.examprep.api.study.StudyPackApplicationService;
import com.examprep.api.study.StudyPackStatusDto;
import com.examprep.api.study.TutorAnswerDto;
import com.examprep.api.study.TutorQuestionDto;
import com.examprep.security.CurrentUserService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StudyApiController {

    private final StudyPackApplicationService studyPackService;
    private final DocumentApplicationService documentService;
    private final CurrentUserService currentUserService;

    public StudyApiController(StudyPackApplicationService studyPackService,
                              DocumentApplicationService documentService,
                              CurrentUserService currentUserService) {
        this.studyPackService = studyPackService;
        this.documentService = documentService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/study/{studyPackId}/status")
    public StudyPackStatusDto studyStatus(@PathVariable UUID studyPackId) {
        return studyPackService.getStatus(studyPackId, currentUserService.requireCurrentUserId());
    }

    @GetMapping("/documents/{documentId}/status")
    public DocumentStatusDto documentStatus(@PathVariable UUID documentId) {
        return documentService.getStatus(documentId, currentUserService.requireCurrentUserId());
    }

    @PostMapping("/study/{studyPackId}/practice/{questionId}")
    public PracticeAnswerDto practiceAnswer(@PathVariable UUID studyPackId,
                                            @PathVariable UUID questionId,
                                            @RequestBody(required = false) PracticeAnswerRequest request) {
        String answer = request == null ? null : request.answer();
        return studyPackService.checkPracticeAnswer(studyPackId,
                currentUserService.requireCurrentUserId(), questionId, answer);
    }

    @PostMapping("/study/{studyPackId}/exam")
    public QuizResultDto submitExam(@PathVariable UUID studyPackId,
                                    @RequestBody QuizSubmissionDto submission) {
        return studyPackService.submitExam(studyPackId, currentUserService.requireCurrentUserId(),
                submission);
    }

    @PostMapping("/study/{studyPackId}/tutor")
    public TutorAnswerDto askTutor(@PathVariable UUID studyPackId,
                                   @Valid @RequestBody TutorQuestionDto question) {
        return studyPackService.askTutor(studyPackId, currentUserService.requireCurrentUserId(), question);
    }

    @PostMapping("/study/{studyPackId}/flashcards/{flashcardId}/review")
    public ResponseEntity<Void> reviewFlashcard(@PathVariable UUID studyPackId,
                                                @PathVariable UUID flashcardId) {
        studyPackService.recordFlashcardReview(studyPackId,
                currentUserService.requireCurrentUserId(), flashcardId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    public record PracticeAnswerRequest(String answer) {
    }
}
