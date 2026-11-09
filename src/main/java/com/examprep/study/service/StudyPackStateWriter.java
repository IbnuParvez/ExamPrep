package com.examprep.study.service;

import com.examprep.ai.service.FlashcardDraft;
import com.examprep.ai.service.QuestionDraft;
import com.examprep.ai.service.StudyMaterialDraft;
import com.examprep.ai.service.TutorAnswerGenerator;
import com.examprep.study.domain.Flashcard;
import com.examprep.study.domain.Question;
import com.examprep.study.domain.StudyPack;
import com.examprep.study.domain.StudyPackContent;
import com.examprep.study.domain.TutorMessage;
import com.examprep.study.repository.FlashcardRepository;
import com.examprep.study.repository.QuestionRepository;
import com.examprep.study.repository.StudyPackRepository;
import com.examprep.study.repository.TutorMessageRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional state changes for asynchronous generation. The AI call itself never runs inside
 * a database transaction.
 */
@Component
public class StudyPackStateWriter {

    public static final String INTERRUPTED_REASON =
            "Generation was interrupted when the application restarted. Please generate the study package again.";

    private final StudyPackRepository studyPackRepository;
    private final FlashcardRepository flashcardRepository;
    private final QuestionRepository questionRepository;
    private final TutorMessageRepository tutorMessageRepository;

    public StudyPackStateWriter(StudyPackRepository studyPackRepository,
                                FlashcardRepository flashcardRepository,
                                QuestionRepository questionRepository,
                                TutorMessageRepository tutorMessageRepository) {
        this.studyPackRepository = studyPackRepository;
        this.flashcardRepository = flashcardRepository;
        this.questionRepository = questionRepository;
        this.tutorMessageRepository = tutorMessageRepository;
    }

    @Transactional
    public void markProcessing(UUID studyPackId, String message) {
        studyPackRepository.findById(studyPackId).ifPresent(pack -> pack.markProcessing(message));
    }

    @Transactional
    public void updateProgress(UUID studyPackId, String message) {
        studyPackRepository.findById(studyPackId).ifPresent(pack -> pack.updateProgress(message));
    }

    @Transactional
    public void complete(UUID studyPackId, StudyMaterialDraft draft) {
        StudyPack pack = studyPackRepository.findById(studyPackId).orElse(null);
        if (pack == null) {
            return;
        }
        flashcardRepository.deleteAllForPack(studyPackId);
        questionRepository.deleteAllForPack(studyPackId);

        List<Flashcard> flashcards = new ArrayList<>(draft.flashcards().size());
        int flashcardPosition = 0;
        for (FlashcardDraft card : draft.flashcards()) {
            flashcards.add(new Flashcard(studyPackId, flashcardPosition++, card.front(), card.back(),
                    card.sourceReference()));
        }
        flashcardRepository.saveAll(flashcards);

        List<Question> questions = new ArrayList<>(draft.questions().size());
        int questionPosition = 0;
        for (QuestionDraft question : draft.questions()) {
            questions.add(new Question(studyPackId, questionPosition++, question.type(),
                    question.prompt(), question.options(), question.correctAnswer(), question.explanation(),
                    question.difficulty(), question.sourceReference(), question.examEligible()));
        }
        questionRepository.saveAll(questions);

        pack.markReady(new StudyPackContent(draft.title(), draft.introduction(), draft.summary(),
                draft.learningObjectives(), draft.revisionPriorities(), draft.topics(),
                draft.revisionNotes(), draft.definitions(), draft.formulas(), draft.keyFacts(),
                draft.examDurationMinutes()));
    }

    @Transactional
    public void fail(UUID studyPackId, String reason) {
        studyPackRepository.findById(studyPackId).ifPresent(pack -> pack.markFailed(reason));
    }

    @Transactional
    public void saveTutorExchange(UUID studyPackId, UUID ownerId, String question,
                                  TutorAnswerGenerator.TutorAnswer answer) {
        tutorMessageRepository.save(TutorMessage.userQuestion(studyPackId, ownerId, question));
        tutorMessageRepository.save(TutorMessage.assistantAnswer(studyPackId, ownerId, answer.answer(),
                answer.references()));
    }
}
