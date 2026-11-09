package com.examprep.api;

import java.util.List;
import java.util.UUID;

import com.examprep.api.dto.ExamSessionDto;
import com.examprep.api.dto.PracticeAnswerDto;
import com.examprep.api.dto.QuizAttemptSummaryDto;
import com.examprep.api.dto.QuizResultDto;
import com.examprep.api.dto.QuizSubmissionDto;
import com.examprep.api.dto.StudyPackCreationResult;
import com.examprep.api.dto.StudyPackDto;
import com.examprep.api.dto.StudyPackStatusDto;
import com.examprep.api.dto.StudyPackSummaryDto;
import com.examprep.api.dto.StudyPreferences;
import com.examprep.api.dto.TutorAnswerDto;
import com.examprep.api.dto.TutorMessageDto;
import com.examprep.api.dto.TutorQuestionDto;

/** Application-level contract for generated study material, examinations and the tutor. */
public interface StudyPackApplicationService {

    StudyPackCreationResult create(UUID documentId, UUID ownerId, StudyPreferences preferences);

    StudyPackStatusDto getStatus(UUID studyPackId, UUID ownerId);

    StudyPackDto getStudyPack(UUID studyPackId, UUID ownerId);

    List<StudyPackSummaryDto> listStudyPacks(UUID ownerId);

    void delete(UUID studyPackId, UUID ownerId);

    ExamSessionDto startExam(UUID studyPackId, UUID ownerId);

    ExamSessionDto getExamSession(UUID attemptId, UUID ownerId);

    QuizResultDto submitExam(UUID studyPackId, UUID ownerId, QuizSubmissionDto submission);

    QuizResultDto getResult(UUID attemptId, UUID ownerId);

    List<QuizAttemptSummaryDto> recentAttempts(UUID ownerId, int limit);

    PracticeAnswerDto revealPracticeAnswer(UUID studyPackId, UUID questionId, UUID ownerId);

    void recordFlashcardReview(UUID studyPackId, UUID ownerId, int reviewedCards, int totalCards);

    TutorAnswerDto askTutor(UUID studyPackId, UUID ownerId, TutorQuestionDto question);

    List<TutorMessageDto> tutorHistory(UUID studyPackId, UUID ownerId);
}
