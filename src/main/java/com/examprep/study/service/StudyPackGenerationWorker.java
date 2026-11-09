package com.examprep.study.service;

import com.examprep.ai.exception.AiConfigurationException;
import com.examprep.ai.exception.AiGenerationException;
import com.examprep.ai.exception.AiUnavailableException;
import com.examprep.ai.service.StudyMaterialDraft;
import com.examprep.ai.service.StudyMaterialGenerator;
import com.examprep.api.document.DocumentContext;
import com.examprep.api.study.StudyPreferences;
import com.examprep.common.ValidationException;
import com.examprep.api.document.DocumentApplicationService;
import com.examprep.study.domain.StudyPack;
import com.examprep.study.repository.StudyPackRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Runs study package generation outside the request thread and records a terminal state for
 * every outcome.
 */
@Service
public class StudyPackGenerationWorker {

    private static final Logger log = LoggerFactory.getLogger(StudyPackGenerationWorker.class);

    private final StudyPackRepository studyPackRepository;
    private final StudyPackStateWriter stateWriter;
    private final StudyMaterialGenerator generator;
    private final DocumentApplicationService documentApplicationService;

    public StudyPackGenerationWorker(StudyPackRepository studyPackRepository,
                                     StudyPackStateWriter stateWriter,
                                     StudyMaterialGenerator generator,
                                     DocumentApplicationService documentApplicationService) {
        this.studyPackRepository = studyPackRepository;
        this.stateWriter = stateWriter;
        this.generator = generator;
        this.documentApplicationService = documentApplicationService;
    }

    @Async("aiTaskExecutor")
    public void generate(UUID studyPackId) {
        StudyPack pack = studyPackRepository.findById(studyPackId).orElse(null);
        if (pack == null || pack.getStatus().isTerminal()) {
            return;
        }
        stateWriter.markProcessing(studyPackId, "Preparing your document...");

        StudyPack current = studyPackRepository.findById(studyPackId).orElse(null);
        if (current == null) {
            return;
        }
        try {
            DocumentContext context = documentApplicationService.getContext(current.getDocumentId(),
                    current.getOwnerId());
            StudyPreferences preferences = new StudyPreferences(current.getLanguage(),
                    current.getAcademicLevel(), current.getExamDate(), current.getRequestedQuestionCount(),
                    current.getRequestedFlashcardCount(), current.getExamDurationMinutes());

            StudyMaterialDraft draft = generator.generate(context, preferences,
                    message -> stateWriter.updateProgress(studyPackId, message));
            stateWriter.complete(studyPackId, draft);
            log.info("Generated study package {} with {} flashcards and {} questions", studyPackId,
                    draft.flashcards().size(), draft.questions().size());
        } catch (AiConfigurationException configuration) {
            stateWriter.fail(studyPackId, configuration.getMessage());
            log.warn("Study package {} failed because the AI integration is not configured", studyPackId);
        } catch (AiUnavailableException | AiGenerationException unavailable) {
            stateWriter.fail(studyPackId, unavailable.getMessage());
            log.warn("Study package {} failed during AI generation: {}", studyPackId,
                    unavailable.getClass().getSimpleName());
        } catch (ValidationException invalidSource) {
            stateWriter.fail(studyPackId, invalidSource.getMessage());
        } catch (Exception unexpected) {
            log.error("Unexpected generation failure for study package {}", studyPackId, unexpected);
            stateWriter.fail(studyPackId,
                    "The study package could not be generated because of an unexpected error.");
        }
    }
}
