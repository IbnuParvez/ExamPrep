package com.examprep.study.service;

import com.examprep.api.study.ExamSummaryDto;
import com.examprep.api.study.FlashcardDto;
import com.examprep.api.study.QuestionDto;
import com.examprep.api.study.QuizResultItemDto;
import com.examprep.api.study.StudyPackDto;
import com.examprep.api.study.StudyPackSummaryDto;
import com.examprep.study.domain.Flashcard;
import com.examprep.study.domain.Question;
import com.examprep.study.domain.QuizAttempt;
import com.examprep.study.domain.StudyPack;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Maps persisted study data to the shared read models. Question mapping deliberately drops the
 * correct answer, explanation and source answer key.
 */
@Component
public class StudyPackMapper {

    public StudyPackDto toDto(StudyPack pack, List<Flashcard> flashcards, List<Question> questions,
                              String documentFilename) {
        List<QuestionDto> practice = new ArrayList<>();
        List<QuestionDto> exam = new ArrayList<>();
        for (Question question : questions) {
            QuestionDto dto = toQuestionDto(question);
            practice.add(dto);
            if (question.isExamEligible()) {
                exam.add(dto);
            }
        }
        List<FlashcardDto> cards = flashcards.stream().map(this::toFlashcardDto).toList();
        return new StudyPackDto(pack.getId(), pack.getDocumentId(), documentFilename, pack.getTitle(),
                pack.getStatus(), pack.getLanguage(), pack.getAcademicLevel(), pack.getIntroduction(),
                pack.getSummary(), nullSafe(pack.getLearningObjectives()),
                nullSafe(pack.getRevisionPriorities()), nullSafe(pack.getTopics()),
                nullSafe(pack.getRevisionNotes()), nullSafe(pack.getDefinitions()),
                nullSafe(pack.getFormulas()), nullSafe(pack.getKeyFacts()), cards, practice, exam,
                pack.getExamDurationMinutes(), pack.getFailureReason(), pack.getCreatedAt());
    }

    public QuestionDto toQuestionDto(Question question) {
        return new QuestionDto(question.getId(), question.getType(), question.getPrompt(),
                nullSafe(question.getOptions()), question.getDifficulty(), question.getSourceReference());
    }

    public FlashcardDto toFlashcardDto(Flashcard flashcard) {
        return new FlashcardDto(flashcard.getId(), flashcard.getFront(), flashcard.getBack(),
                flashcard.getSourceReference());
    }

    public StudyPackSummaryDto toSummary(StudyPack pack, int flashcardCount, int questionCount,
                                         QuizAttempt latestAttempt) {
        return new StudyPackSummaryDto(pack.getId(), pack.getDocumentId(), pack.getTitle(), null,
                pack.getStatus(), flashcardCount, questionCount,
                latestAttempt == null ? null : latestAttempt.getScore(),
                latestAttempt == null ? null : latestAttempt.getTotalQuestions(),
                pack.getCreatedAt());
    }

    public ExamSummaryDto toExamSummary(QuizAttempt attempt, String studyPackTitle) {
        return new ExamSummaryDto(attempt.getId(), attempt.getStudyPackId(), studyPackTitle,
                attempt.getScore(), attempt.getTotalQuestions(), attempt.getTimeTakenSeconds(),
                attempt.getSubmittedAt());
    }

    /**
     * Produces revision pointers from the source locations of incorrect answers.
     */
    public List<String> revisionAreas(List<QuizResultItemDto> items) {
        Set<String> areas = new LinkedHashSet<>();
        for (QuizResultItemDto item : items) {
            if (item.correct()) {
                continue;
            }
            String location = item.sourceReference();
            areas.add(location == null || location.isBlank()
                    ? "Review this topic in your document"
                    : "Revisit " + location);
            if (areas.size() >= 6) {
                break;
            }
        }
        return List.copyOf(areas);
    }

    private static <T> List<T> nullSafe(List<T> value) {
        return value == null ? List.of() : value;
    }
}
