package com.examprep.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.examprep.ai.dto.GeneratedStudyPackage;
import com.examprep.ai.exception.AiResponseException;
import com.examprep.api.DocumentApplicationService;
import com.examprep.api.dto.DocumentContext;
import com.examprep.api.dto.StudyPackDto;
import com.examprep.api.dto.StudyPreferences;
import com.examprep.api.model.Difficulty;
import com.examprep.api.model.QuestionType;
import com.examprep.common.AppException;
import com.examprep.config.AsyncConfig;
import com.examprep.study.domain.FlashcardEntity;
import com.examprep.study.domain.QuestionEntity;
import com.examprep.study.domain.StudyPackageContent;
import com.examprep.study.domain.StudyPack;
import com.examprep.study.repository.FlashcardRepository;
import com.examprep.study.repository.QuestionRepository;
import com.examprep.study.repository.StudyPackRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Runs study generation outside the browser request. Progress is persisted so polling clients see
 * honest status, and failures are stored with a safe explanation instead of a fake result.
 *
 * <p>No transaction is ever held open across an AI HTTP call: each short write re-reads the pack
 * inside its own transaction so a package deleted while the model was working is never resurrected.
 */
@Service
public class StudyGenerationService {

    private static final Logger log = LoggerFactory.getLogger(StudyGenerationService.class);

    private final StudyPackRepository studyPackRepository;
    private final FlashcardRepository flashcardRepository;
    private final QuestionRepository questionRepository;
    private final DocumentApplicationService documentService;
    private final StudyPackageGenerator generator;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public StudyGenerationService(StudyPackRepository studyPackRepository, FlashcardRepository flashcardRepository,
                                  QuestionRepository questionRepository, DocumentApplicationService documentService,
                                  StudyPackageGenerator generator, ObjectMapper objectMapper,
                                  PlatformTransactionManager transactionManager) {
        this.studyPackRepository = studyPackRepository;
        this.flashcardRepository = flashcardRepository;
        this.questionRepository = questionRepository;
        this.documentService = documentService;
        this.generator = generator;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Async(AsyncConfig.STUDY_EXECUTOR)
    public void generate(UUID studyPackId) {
        StudyPack pack = studyPackRepository.findById(studyPackId).orElse(null);
        if (pack == null) {
            return;
        }
        try {
            markProcessing(studyPackId, "Reading extracted document text", 10);

            DocumentContext context = documentService.getContext(pack.getDocumentId(), pack.getOwnerId());
            StudyPreferences preferences = new StudyPreferences(
                    pack.getLanguage(), pack.getAcademicLevel(), pack.getExamDate(),
                    defaultValue(pack.getQuestionTarget(), 12),
                    defaultValue(pack.getFlashcardTarget(), 16),
                    defaultValue(pack.getExamQuestionCount(), 10),
                    defaultValue(pack.getExamDurationMinutes(), 15));

            GeneratedStudyPackage generated = generator.generate(context, preferences,
                    (message, percent) -> markProcessing(studyPackId, message, percent));

            saveGeneratedPackage(studyPackId, generated);
            log.info("Study package {} is ready", studyPackId);
        } catch (AppException exception) {
            log.warn("Study generation failed for package {}: {}", studyPackId, exception.getMessage());
            fail(studyPackId, exception.getMessage());
        } catch (Exception exception) {
            log.warn("Study generation failed unexpectedly for package {}", studyPackId, exception);
            fail(studyPackId, "We could not generate this study package. Please try again.");
        }
    }

    /** Persists one progress update in its own short transaction. */
    private void markProcessing(UUID studyPackId, String message, int percent) {
        transactionTemplate.executeWithoutResult(status -> studyPackRepository.findById(studyPackId)
                .ifPresent(current -> {
                    current.markProcessing(message, percent);
                    studyPackRepository.save(current);
                }));
    }

    /**
     * Persists questions, flashcards, package content and the READY status in one transaction. These used
     * to be five separate auto-committed saves, so a failure part-way left flashcards and questions
     * attached to a package that had been marked FAILED.
     */
    private void saveGeneratedPackage(UUID studyPackId, GeneratedStudyPackage generated) {
        transactionTemplate.executeWithoutResult(status -> {
            StudyPack current = studyPackRepository.findById(studyPackId).orElse(null);
            if (current == null) {
                // The package was deleted while the model was working; never resurrect it.
                return;
            }
            persistQuestionsAndFlashcards(current, generated);
            Map<String, Object> contentMap = objectMapper.convertValue(buildContent(generated),
                    new TypeReference<>() {
                    });
            current.applyGeneratedContent(generated.title(), generated.introduction(), generated.summary(),
                    generated.learningObjectives(), generated.keyAreasToRevise(), contentMap);
            current.markReady();
            studyPackRepository.save(current);
        });
    }

    private StudyPackageContent buildContent(GeneratedStudyPackage generated) {
        return new StudyPackageContent(
                generated.topics().stream()
                        .map(topic -> new StudyPackDto.Topic(topic.name(), topic.explanation(),
                                topic.keyConcepts(), topic.sources()))
                        .toList(),
                generated.revisionSections().stream()
                        .map(section -> new StudyPackDto.RevisionSection(section.heading(), section.body(),
                                section.sources()))
                        .toList(),
                generated.definitions().stream()
                        .map(definition -> new StudyPackDto.Definition(definition.term(),
                                definition.definition(), definition.source()))
                        .toList(),
                generated.formulas().stream()
                        .map(formula -> new StudyPackDto.Formula(formula.name(), formula.expression(),
                                formula.explanation(), formula.source()))
                        .toList());
    }

    private void persistQuestionsAndFlashcards(StudyPack pack, GeneratedStudyPackage generated) {
        flashcardRepository.deleteAllForPack(pack.getId());
        questionRepository.deleteAllForPack(pack.getId());

        List<FlashcardEntity> flashcards = new ArrayList<>();
        int flashcardOrdinal = 1;
        for (GeneratedStudyPackage.Flashcard flashcard : generated.flashcards()) {
            flashcards.add(FlashcardEntity.of(pack.getId(), flashcardOrdinal++, flashcard.front(),
                    flashcard.back(), flashcard.source()));
        }
        if (!flashcards.isEmpty()) {
            flashcardRepository.saveAll(flashcards);
        }

        List<QuestionEntity> questions = new ArrayList<>();
        int questionOrdinal = 1;
        for (GeneratedStudyPackage.Question question : generated.questions()) {
            QuestionType type = QuestionType.valueOf(question.type());
            Difficulty difficulty = Difficulty.valueOf(question.difficulty());
            questions.add(QuestionEntity.of(pack.getId(), questionOrdinal++, type, question.question(),
                    question.options(), question.correctOptionIndex(), question.correctAnswer(),
                    question.suggestedAnswer(), question.explanation(), difficulty, question.source()));
        }
        questionRepository.saveAll(questions);
    }

    private void fail(UUID studyPackId, String message) {
        transactionTemplate.executeWithoutResult(status -> studyPackRepository.findById(studyPackId)
                .ifPresent(current -> {
                    // A deleted package (for example a deleted document) must not be resurrected.
                    current.markFailed(message);
                    studyPackRepository.save(current);
                }));
    }

    private int defaultValue(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    /** Used by tests to assert that an unusable response fails loudly. */
    static AiResponseException invalidResponse() {
        return new AiResponseException("The AI service returned unusable study material.");
    }
}
