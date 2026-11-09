package com.examprep.support;

import com.examprep.api.document.SourceReferenceDto;
import com.examprep.api.study.DefinitionDto;
import com.examprep.api.study.Difficulty;
import com.examprep.api.study.FormulaDto;
import com.examprep.api.study.QuestionType;
import com.examprep.api.study.RevisionSectionDto;
import com.examprep.api.study.TopicDto;
import com.examprep.document.domain.Document;
import com.examprep.document.domain.DocumentChunk;
import com.examprep.document.domain.DocumentFormat;
import com.examprep.document.repository.DocumentChunkRepository;
import com.examprep.document.repository.DocumentRepository;
import com.examprep.study.domain.Question;
import com.examprep.study.domain.StudyPack;
import com.examprep.study.domain.StudyPackContent;
import com.examprep.study.repository.QuestionRepository;
import com.examprep.study.repository.StudyPackRepository;
import java.util.List;
import java.util.UUID;

/**
 * Seeds persisted state for tests without going through the AI pipeline.
 */
public final class StudyFixtures {

    public static final String EXPLANATION_MARKER = "EXPLANATION-MARKER-4721";
    public static final String SHORT_ANSWER_MODEL = "Light energy is captured and stored as ATP and NADPH.";

    private StudyFixtures() {
    }

    public static Document seedReadyDocument(DocumentRepository documentRepository,
                                             DocumentChunkRepository chunkRepository,
                                             UUID ownerId) {
        Document document = Document.uploaded(ownerId, "biology.pdf", "application/pdf",
                DocumentFormat.PDF, ownerId + "/seed/biology.pdf", 4096);
        document.markReady(2, 520);
        Document saved = documentRepository.save(document);
        chunkRepository.save(new DocumentChunk(saved.getId(), 0, "S1", "Page 1", null,
                "Photosynthesis captures light energy in chloroplasts using the pigment chlorophyll."));
        chunkRepository.save(new DocumentChunk(saved.getId(), 1, "S2", "Page 2", null,
                "The light reactions produce ATP and NADPH and release oxygen as a by-product."));
        return saved;
    }

    public static StudyPack seedReadyPack(StudyPackRepository repository, UUID ownerId, UUID documentId,
                                          String title) {
        StudyPack pack = StudyPack.queued(ownerId, documentId, title, "English", "Undergraduate",
                null, 4, 4, 25);
        pack.markProcessing("Generating...");
        pack.markReady(new StudyPackContent(
                title,
                "This document introduces photosynthesis and the light reactions.",
                "Photosynthesis converts light energy into chemical energy in chloroplasts.",
                List.of("Describe the role of chlorophyll", "Outline the light reactions"),
                List.of("The thylakoid membrane", "ATP and NADPH production"),
                List.of(new TopicDto("Photosynthesis", "Explains how light energy becomes chemical energy.",
                        List.of("Chlorophyll", "Chloroplast"),
                        List.of(new SourceReferenceDto("S1", "Page 1")))),
                List.of(new RevisionSectionDto("The light reactions",
                        "Light energy excites electrons in chlorophyll, producing ATP and NADPH.",
                        List.of("Water is split", "Oxygen is released"),
                        List.of(new SourceReferenceDto("S2", "Page 2")))),
                List.of(new DefinitionDto("Chlorophyll", "The pigment that absorbs light energy.", "Page 1")),
                List.of(new FormulaDto("Photon energy", "E = hf", "Energy of a single photon.", "Page 2")),
                List.of("Oxygen is released during the light reactions"),
                25));
        return repository.save(pack);
    }

    public static void seedQuestions(QuestionRepository repository, UUID studyPackId) {
        repository.save(new Question(studyPackId, 0, QuestionType.MULTIPLE_CHOICE,
                "Which organelle performs photosynthesis?",
                List.of("Mitochondrion", "Chloroplast", "Ribosome"),
                "Chloroplast", EXPLANATION_MARKER, Difficulty.MEDIUM, "Page 1", true));
        repository.save(new Question(studyPackId, 1, QuestionType.TRUE_FALSE,
                "Photosynthesis releases oxygen.",
                List.of("True", "False"),
                "True", "Oxygen is a by-product of the light reactions.", Difficulty.EASY, "Page 2", true));
        repository.save(new Question(studyPackId, 2, QuestionType.SHORT_ANSWER,
                "Explain the light reactions.",
                List.of(),
                SHORT_ANSWER_MODEL, "Marks are awarded for ATP, NADPH and oxygen.", Difficulty.HARD,
                "Page 2", false));
    }
}
