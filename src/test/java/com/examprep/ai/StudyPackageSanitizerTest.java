package com.examprep.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.examprep.ai.dto.GeneratedStudyPackage;
import com.examprep.ai.exception.AiResponseException;
import com.examprep.ai.retrieval.SourceReferences;
import com.examprep.ai.service.StudyPackageSanitizer;
import com.examprep.api.dto.DocumentChunk;
import com.examprep.api.dto.StudyPreferences;

class StudyPackageSanitizerTest {

    private final StudyPackageSanitizer sanitizer = new StudyPackageSanitizer();
    private final StudyPreferences preferences = StudyPreferences.defaults();
    private final SourceReferences references = SourceReferences.from(List.of(
            new DocumentChunk("c1", 1, "Page 2", "First chunk"),
            new DocumentChunk("c2", 2, "Page 7", "Second chunk")));

    @Test
    void dropsFabricatedCitationsAndKeepsRealOnes() {
        GeneratedStudyPackage raw = new GeneratedStudyPackage(
                "Demand and supply",
                "Introduction",
                "Summary",
                List.of("Objective"),
                List.of("Area"),
                List.of(new GeneratedStudyPackage.Topic("Equilibrium", "Where supply meets demand",
                        List.of("Price"), List.of("c2", "c99", "Page 12"))),
                List.of(new GeneratedStudyPackage.RevisionSection("Notes", "Body", List.of("c1", "c404"))),
                List.of(new GeneratedStudyPackage.Definition("Elasticity", "Responsiveness", "c2")),
                List.of(new GeneratedStudyPackage.Formula("Revenue", "R = P x Q", "Total revenue", "c1")),
                List.of(new GeneratedStudyPackage.Flashcard("What is elasticity?", "Responsiveness", "c2")),
                List.of(mcq("What happens when demand rises?", List.of("Price rises", "Price falls"), 0, "c1")));

        GeneratedStudyPackage clean = sanitizer.sanitize(raw, references, preferences, "Fallback");

        assertThat(clean.topics().get(0).sources()).containsExactly("Page 7");
        assertThat(clean.revisionSections().get(0).sources()).containsExactly("Page 2");
        assertThat(clean.definitions().get(0).source()).isEqualTo("Page 7");
        assertThat(clean.formulas().get(0).source()).isEqualTo("Page 2");
        assertThat(clean.flashcards().get(0).source()).isEqualTo("Page 7");
        assertThat(clean.questions().get(0).source()).isEqualTo("Page 2");
    }

    @Test
    void discardsMalformedQuestionsButKeepsValidOnes() {
        GeneratedStudyPackage raw = new GeneratedStudyPackage(
                "Title", "Intro", "Summary", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new GeneratedStudyPackage.Flashcard("Question?", "Answer", "c1")),
                List.of(
                        mcq("Valid question?", List.of("A", "B", "C"), 1, "c1"),
                        mcq("Broken index", List.of("A", "B"), 5, "c1"),
                        mcq("Too few options", List.of("Only one"), 0, "c1"),
                        new GeneratedStudyPackage.Question("UNKNOWN", "Unknown type", List.of("A", "B"), 0,
                                null, null, "e", "EASY", "c1"),
                        new GeneratedStudyPackage.Question("TRUE_FALSE", "The sky is blue.", List.of(), null,
                                true, null, "It is.", "EASY", "c1"),
                        new GeneratedStudyPackage.Question("SHORT_ANSWER", "Explain elasticity.", List.of(), null,
                                null, "A measure of responsiveness.", "Define it.", "HARD", "c1")));

        GeneratedStudyPackage clean = sanitizer.sanitize(raw, references, preferences, "Fallback");

        assertThat(clean.questions()).hasSize(3);
        assertThat(clean.questions()).extracting(GeneratedStudyPackage.Question::type)
                .containsExactly("MULTIPLE_CHOICE", "TRUE_FALSE", "SHORT_ANSWER");
        assertThat(clean.questions().get(1).options()).containsExactly("True", "False");
        assertThat(clean.questions().get(1).correctOptionIndex()).isEqualTo(0);
    }

    @Test
    void rejectsResponsesWithoutAnyValidQuestion() {
        GeneratedStudyPackage raw = new GeneratedStudyPackage(
                "Title", "Intro", "Summary", List.of(), List.of(),
                List.of(new GeneratedStudyPackage.Topic("Topic", "Explanation", List.of(), List.of("c1"))),
                List.of(), List.of(), List.of(), List.of(), List.of());

        assertThatThrownBy(() -> sanitizer.sanitize(raw, references, preferences, "Fallback"))
                .isInstanceOf(AiResponseException.class)
                .hasMessageContaining("practice questions");
    }

    @Test
    void fallsBackToDocumentTitleWhenModelTitleIsBlank() {
        GeneratedStudyPackage raw = new GeneratedStudyPackage(
                "  ", "Intro", "Summary", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new GeneratedStudyPackage.Flashcard("Q", "A", "c1")),
                List.of(mcq("Question?", List.of("A", "B"), 0, "c1")));

        GeneratedStudyPackage clean = sanitizer.sanitize(raw, references, preferences, "Lecture 3");

        assertThat(clean.title()).isEqualTo("Lecture 3");
    }

    private GeneratedStudyPackage.Question mcq(String question, List<String> options, int correctIndex,
                                               String source) {
        return new GeneratedStudyPackage.Question("MULTIPLE_CHOICE", question, options, correctIndex, null, null,
                "Because the material says so.", "MEDIUM", source);
    }
}
