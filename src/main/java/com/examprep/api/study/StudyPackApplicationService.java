package com.examprep.api.study;

import java.util.List;
import java.util.UUID;

/**
 * Shared study contract. All methods are scoped to {@code ownerId}; a mismatch between the
 * pack owner and the supplied identifier results in a not-found error.
 */
public interface StudyPackApplicationService {

    StudyPackCreationResult create(UUID documentId, UUID ownerId, StudyPreferences preferences);

    StudyPackStatusDto getStatus(UUID studyPackId, UUID ownerId);

    StudyPackDto getStudyPack(UUID studyPackId, UUID ownerId);

    List<StudyPackSummaryDto> listStudyPacks(UUID ownerId);

    StudyProgressDto getProgress(UUID ownerId);

    List<QuestionDto> getExamQuestions(UUID studyPackId, UUID ownerId);

    List<QuestionDto> getPracticeQuestions(UUID studyPackId, UUID ownerId);

    List<FlashcardDto> getFlashcards(UUID studyPackId, UUID ownerId);

    PracticeAnswerDto checkPracticeAnswer(UUID studyPackId, UUID ownerId, UUID questionId, String answer);

    QuizResultDto submitExam(UUID studyPackId, UUID ownerId, QuizSubmissionDto submission);

    QuizResultDto getAttempt(UUID studyPackId, UUID ownerId, UUID attemptId);

    TutorAnswerDto askTutor(UUID studyPackId, UUID ownerId, TutorQuestionDto question);

    List<TutorMessageDto> getTutorConversation(UUID studyPackId, UUID ownerId);

    void recordFlashcardReview(UUID studyPackId, UUID ownerId, UUID flashcardId);

    void delete(UUID studyPackId, UUID ownerId);
}
