package com.examprep.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
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
        GeneratedStudyPackage raw = packageWith(mcq("What happens when demand rises?",
                List.of("Price rises", "Price falls"), 0, "c1"));

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
        List<GeneratedStudyPackage.Question> questions = new ArrayList<>(List.of(
                mcq("Valid question?", List.of("A", "B", "C"), 1, "c1"),
                mcq("Broken index", List.of("A", "B"), 5, "c1"),
                mcq("Too few options", List.of("Only one"), 0, "c1"),
                new GeneratedStudyPackage.Question("UNKNOWN", "Unknown type", List.of("A", "B"), 0,
                        null, null, "e", "EASY", "c1"),
                new GeneratedStudyPackage.Question("TRUE_FALSE", "The sky is blue.", List.of(), null,
                        true, null, "It is.", "EASY", "c1"),
                new GeneratedStudyPackage.Question("SHORT_ANSWER", "Explain elasticity.", List.of(), null,
                        null, "A measure of responsiveness.", "Define it.", "HARD", "c1")));
        // A mock examination needs four auto-scored questions, so add three more valid ones.
        questions.addAll(fillers());

        GeneratedStudyPackage clean = sanitizer.sanitize(
                withQuestions("Demand and supply",
                        List.of(new GeneratedStudyPackage.Flashcard("Question?", "Answer", "c1")), questions),
                references, preferences, "Fallback");

        assertThat(clean.questions()).hasSize(6);
        assertThat(clean.questions()).extracting(GeneratedStudyPackage.Question::type)
                .containsExactly("MULTIPLE_CHOICE", "TRUE_FALSE", "SHORT_ANSWER", "MULTIPLE_CHOICE",
                        "MULTIPLE_CHOICE", "MULTIPLE_CHOICE");
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
    void rejectsPackagesThatCouldNotRunAMockExamination() {
        GeneratedStudyPackage raw = withQuestions("Demand and supply", List.of(
                new GeneratedStudyPackage.Flashcard("Question?", "Answer", "c1")),
                List.of(mcq("Only one auto-scored question?", List.of("A", "B"), 0, "c1"),
                        new GeneratedStudyPackage.Question("SHORT_ANSWER", "Explain elasticity.", List.of(),
                                null, null, "Responsiveness.", "Define it.", "HARD", "c1")));

        assertThatThrownBy(() -> sanitizer.sanitize(raw, references, preferences, "Fallback"))
                .isInstanceOf(AiResponseException.class)
                .hasMessageContaining("mock examination");
    }

    @Test
    void remapsCorrectIndexWhenDuplicateOptionsAreRemoved() {
        // [Increase, Increase, Decrease, No change] with the answer at index 2: de-duplication removes
        // index 1, so the stored index must move from 2 to 1 instead of silently pointing at "No change".
        GeneratedStudyPackage clean = sanitizer.sanitize(
                packageWith(mcq("What happens to price when supply is fixed and demand rises?",
                        List.of("Increase", "Increase", "Decrease", "No change"), 2, "c1")),
                references, preferences, "Fallback");

        GeneratedStudyPackage.Question question = clean.questions().get(0);
        assertThat(question.options()).containsExactly("Increase", "Decrease", "No change");
        assertThat(question.correctOptionIndex()).isEqualTo(1);
        assertThat(question.options().get(question.correctOptionIndex())).isEqualTo("Decrease");
    }

    @Test
    void fallsBackToDocumentTitleWhenModelTitleIsBlank() {
        GeneratedStudyPackage clean = sanitizer.sanitize(
                packageWith("   ", mcq("Question?", List.of("A", "B"), 0, "c1")),
                references, preferences, "Lecture 3");

        assertThat(clean.title()).isEqualTo("Lecture 3");
    }

    /** A package with the supplied question plus three more auto-scored ones. */
    private GeneratedStudyPackage packageWith(GeneratedStudyPackage.Question question) {
        return packageWith("Demand and supply", question);
    }

    private GeneratedStudyPackage packageWith(String title, GeneratedStudyPackage.Question question) {
        List<GeneratedStudyPackage.Question> questions = new ArrayList<>();
        questions.add(question);
        questions.addAll(fillers());
        return withQuestions(title, List.of(
                new GeneratedStudyPackage.Topic("Equilibrium", "Where supply meets demand",
                        List.of("Price"), List.of("c2", "c99", "Page 12")),
                new GeneratedStudyPackage.RevisionSection("Notes", "Body", List.of("c1", "c404")),
                new GeneratedStudyPackage.Definition("Elasticity", "Responsiveness", "c2"),
                new GeneratedStudyPackage.Formula("Revenue", "R = P x Q", "Total revenue", "c1"),
                new GeneratedStudyPackage.Flashcard("What is elasticity?", "Responsiveness", "c2")),
                questions);
    }

    private GeneratedStudyPackage withQuestions(String title, List<Object> extra,
                                                List<GeneratedStudyPackage.Question> questions) {
        List<GeneratedStudyPackage.Topic> topics = List.of();
        List<GeneratedStudyPackage.RevisionSection> sections = List.of();
        List<GeneratedStudyPackage.Definition> definitions = List.of();
        List<GeneratedStudyPackage.Formula> formulas = List.of();
        List<GeneratedStudyPackage.Flashcard> flashcards = List.of();
        for (Object item : extra) {
            if (item instanceof GeneratedStudyPackage.Topic topic) {
                topics = List.of(topic);
            } else if (item instanceof GeneratedStudyPackage.RevisionSection section) {
                sections = List.of(section);
            } else if (item instanceof GeneratedStudyPackage.Definition definition) {
                definitions = List.of(definition);
            } else if (item instanceof GeneratedStudyPackage.Formula formula) {
                formulas = List.of(formula);
            } else if (item instanceof GeneratedStudyPackage.Flashcard flashcard) {
                flashcards = List.of(flashcard);
            }
        }
        return new GeneratedStudyPackage(title, "Introduction", "Summary",
                List.of("Objective"), List.of("Area"), topics, sections, definitions, formulas, flashcards,
                questions);
    }

    private List<GeneratedStudyPackage.Question> fillers() {
        return List.of(
                mcq("Filler one?", List.of("A", "B"), 0, "c1"),
                mcq("Filler two?", List.of("A", "B"), 1, "c1"),
                mcq("Filler three?", List.of("A", "B"), 0, "c1"));
    }

    private GeneratedStudyPackage.Question mcq(String question, List<String> options, int correctIndex,
                                               String source) {
        return new GeneratedStudyPackage.Question("MULTIPLE_CHOICE", question, options, correctIndex, null, null,
                "Because the material says so.", "MEDIUM", source);
    }
}
