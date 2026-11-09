package com.examprep.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.examprep.ai.client.GeminiTransport;
import com.examprep.ai.client.GoogleGeminiGateway;
import com.examprep.ai.config.GeminiProperties;
import com.examprep.ai.exception.AiConfigurationException;
import com.examprep.ai.exception.AiGenerationException;
import com.examprep.ai.exception.AiRateLimitException;
import com.examprep.ai.retrieval.SourceReferenceResolver;
import com.examprep.ai.service.GeminiJsonClient;
import com.examprep.ai.service.StudyMaterialDraft;
import com.examprep.ai.service.StudyMaterialGenerator;
import com.examprep.api.document.DocumentChunkDto;
import com.examprep.api.document.DocumentContext;
import com.examprep.api.study.QuestionType;
import com.examprep.api.study.StudyPreferences;
import com.examprep.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StudyMaterialGenerationTest {

    private static final String VALID_JSON = """
            {
              "title": "Photosynthesis",
              "introduction": "An introduction to photosynthesis.",
              "summary": "Photosynthesis converts light energy into chemical energy inside chloroplasts.",
              "learningObjectives": ["Describe the light reactions"],
              "revisionPriorities": ["The thylakoid membrane"],
              "topics": [
                {"title": "Photosynthesis", "explanation": "Light energy becomes chemical energy.",
                 "keyConcepts": ["Chlorophyll"], "sourceRefs": ["S1", "S77"]}
              ],
              "revisionSections": [
                {"heading": "The light reactions", "narrative": "Water is split and oxygen is released.",
                 "keyPoints": ["ATP is produced"], "sourceRefs": ["S2"]}
              ],
              "definitions": [
                {"term": "Chlorophyll", "definition": "The pigment that absorbs light.", "sourceRef": "S1"}
              ],
              "formulas": [
                {"name": "Photon energy", "expression": "E = hf", "explanation": "Energy of a photon.", "sourceRef": "S2"}
              ],
              "keyFacts": ["Oxygen is released"],
              "flashcards": [{"front": "Where does photosynthesis happen?", "back": "In the chloroplast.", "sourceRef": "S1"}],
              "questions": [
                {"type": "MULTIPLE_CHOICE", "prompt": "Which organelle performs photosynthesis?",
                 "options": ["Chloroplast", "Nucleus"], "correctAnswer": "Chloroplast",
                 "explanation": "Chloroplasts contain chlorophyll.", "difficulty": "EASY", "sourceRef": "S1"},
                {"type": "TRUE_FALSE", "prompt": "Photosynthesis releases oxygen.",
                 "options": [], "correctAnswer": "True",
                 "explanation": "Oxygen is a by-product.", "difficulty": "EASY", "sourceRef": "S2"},
                {"type": "SHORT_ANSWER", "prompt": "Explain the light reactions.",
                 "options": [], "correctAnswer": "Light energy is captured as ATP.",
                 "explanation": "ATP and NADPH are produced.", "difficulty": "HARD", "sourceRef": "S2"}
              ],
              "examDurationMinutes": 25
            }
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AppProperties properties = new AppProperties(
            new AppProperties.Storage("target/test-uploads"),
            new AppProperties.Upload(25),
            new AppProperties.Processing(30, 1_500_000, 2400, 1800, 200),
            new AppProperties.Study(24_000, 12_000, 10, 10, 30, 40, 60),
            new AppProperties.Tutor(6));

    @Test
    void buildsAValidatedDraftAndDropsInventedCitations() {
        GeminiTransport transport = mock(GeminiTransport.class);
        when(transport.generateContent(any())).thenReturn(VALID_JSON);

        StudyMaterialDraft draft = generator(transport).generate(context(), StudyPreferences.defaults(),
                message -> { });

        assertThat(draft.title()).isEqualTo("Photosynthesis");
        assertThat(draft.summary()).contains("chloroplasts");
        assertThat(draft.topics()).hasSize(1);
        assertThat(draft.topics().get(0).sourceReferences())
                .extracting(reference -> reference.referenceId())
                .containsExactly("S1");
        assertThat(draft.revisionNotes()).hasSize(1);
        assertThat(draft.definitions()).hasSize(1);
        assertThat(draft.definitions().get(0).sourceReference()).isEqualTo("Page 1");
        assertThat(draft.formulas()).hasSize(1);
        assertThat(draft.flashcards()).hasSize(1);
        assertThat(draft.flashcards().get(0).sourceReference()).isEqualTo("Page 1");
        assertThat(draft.questions()).hasSize(3);
        assertThat(draft.questions().get(0).type()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(draft.questions().get(0).examEligible()).isTrue();
        assertThat(draft.questions().get(1).type()).isEqualTo(QuestionType.TRUE_FALSE);
        assertThat(draft.questions().get(1).correctAnswer()).isEqualTo("True");
        assertThat(draft.questions().get(2).type()).isEqualTo(QuestionType.SHORT_ANSWER);
        assertThat(draft.questions().get(2).examEligible()).isFalse();
        assertThat(draft.examDurationMinutes()).isEqualTo(25);
    }

    @Test
    void rejectsUnusableJsonAfterARepairAttempt() {
        GeminiTransport transport = mock(GeminiTransport.class);
        when(transport.generateContent(any())).thenReturn("{\"summary\":\"incomplete\"}");

        Throwable failure = catchThrowable(() -> generator(transport)
                .generate(context(), StudyPreferences.defaults(), message -> { }));

        assertThat(failure).isInstanceOf(AiGenerationException.class);
        verify(transport, times(2)).generateContent(any());
    }

    @Test
    void retriesRateLimitedRequests() {
        GeminiTransport transport = mock(GeminiTransport.class);
        when(transport.generateContent(any()))
                .thenThrow(new AiRateLimitException("Too many requests"))
                .thenReturn(VALID_JSON);

        StudyMaterialDraft draft = generator(transport).generate(context(), StudyPreferences.defaults(),
                message -> { });

        assertThat(draft.title()).isEqualTo("Photosynthesis");
        verify(transport, times(2)).generateContent(any());
    }

    @Test
    void failsWithAClearMessageWhenNoApiKeyIsConfigured() {
        GeminiTransport transport = mock(GeminiTransport.class);
        GeminiProperties missingKey = new GeminiProperties("", "gemini-3.8-flash", 30, 1, 4096);

        Throwable failure = catchThrowable(() -> generator(transport, missingKey)
                .generate(context(), StudyPreferences.defaults(), message -> { }));

        assertThat(failure).isInstanceOf(AiConfigurationException.class)
                .hasMessageContaining("GOOGLE_API_KEY");
    }

    private StudyMaterialGenerator generator(GeminiTransport transport) {
        return generator(transport, new GeminiProperties("test-key", "gemini-3.8-flash", 30, 1, 4096));
    }

    private StudyMaterialGenerator generator(GeminiTransport transport, GeminiProperties geminiProperties) {
        GoogleGeminiGateway gateway = new GoogleGeminiGateway(transport, geminiProperties);
        GeminiJsonClient jsonClient = new GeminiJsonClient(gateway, geminiProperties, objectMapper);
        return new StudyMaterialGenerator(jsonClient, new SourceReferenceResolver(), properties, objectMapper);
    }

    private DocumentContext context() {
        return new DocumentContext(UUID.randomUUID(), UUID.randomUUID(), "biology.pdf", List.of(
                new DocumentChunkDto("S1", "Page 1", null,
                        "Photosynthesis captures light energy in chloroplasts using chlorophyll.", 0),
                new DocumentChunkDto("S2", "Page 2", null,
                        "The light reactions produce ATP and NADPH and release oxygen.", 1)));
    }
}
