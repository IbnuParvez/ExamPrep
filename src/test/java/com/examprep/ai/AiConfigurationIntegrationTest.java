package com.examprep.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import com.examprep.ai.config.GeminiClientFactory;
import com.examprep.ai.exception.AiUnavailableException;
import com.examprep.ai.retrieval.SourceReferences;
import com.examprep.api.DocumentApplicationService;
import com.examprep.api.StudyPackApplicationService;
import com.examprep.api.UserRegistrationService;
import com.examprep.api.dto.DocumentUploadResult;
import com.examprep.api.dto.RegisterUserRequest;
import com.examprep.api.dto.StudyPackCreationResult;
import com.examprep.api.dto.StudyPackDto;
import com.examprep.api.dto.StudyPreferences;
import com.examprep.api.dto.TutorQuestionDto;
import com.examprep.api.dto.UserAccountDto;
import com.examprep.api.model.DocumentStatus;
import com.examprep.api.model.StudyPackStatus;
import com.examprep.config.AiProperties;
import com.examprep.support.AbstractIntegrationTest;

/** With no API key configured, AI features must fail clearly instead of returning fake content. */
class AiConfigurationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private StudyPackApplicationService studyPackService;

    @Autowired
    private DocumentApplicationService documentService;

    @Autowired
    private UserRegistrationService registrationService;

    @Test
    void missingApiKeyThrowsAnActionableConfigurationError() {
        AiProperties properties = new AiProperties();
        properties.setApiKey("");
        GeminiClientFactory factory = new GeminiClientFactory(properties);

        assertThatThrownBy(factory::client)
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("GOOGLE_API_KEY");
    }

    @Test
    void generationWithoutApiKeyFailsWithAStoredExplanation() {
        UserAccountDto owner = registrationService.register(new RegisterUserRequest("No Key",
                "nokey-" + UUID.randomUUID() + "@example.com", "study-password-1"));
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", """
                Demand and supply explain how prices form in a competitive market. When demand rises and supply
                is fixed, the equilibrium price increases. Elasticity measures how strongly quantity responds to
                a price change: demand is elastic when quantity changes a lot, and inelastic when it barely
                changes. Supply elasticity depends on production capacity and on how quickly producers can
                adjust. These relationships are used to analyse taxation, price controls and market shocks.
                """.getBytes(StandardCharsets.UTF_8));
        DocumentUploadResult upload = documentService.upload(file, owner.id());
        await(Duration.ofSeconds(20), () ->
                documentService.getStatus(upload.documentId(), owner.id()).status() == DocumentStatus.READY);

        StudyPackCreationResult creation = studyPackService.create(upload.documentId(), owner.id(),
                StudyPreferences.defaults());
        await(Duration.ofSeconds(20), () ->
                studyPackService.getStatus(creation.studyPackId(), owner.id()).status() == StudyPackStatus.FAILED);

        assertThat(studyPackService.getStatus(creation.studyPackId(), owner.id()).errorMessage())
                .contains("GOOGLE_API_KEY");

        StudyPackDto pack = studyPackService.getStudyPack(creation.studyPackId(), owner.id());
        assertThat(pack.flashcards()).isEmpty();
        assertThat(pack.topics()).isEmpty();

        assertThatThrownBy(() -> studyPackService.askTutor(creation.studyPackId(), owner.id(),
                new TutorQuestionDto("Explain elasticity.")))
                .isInstanceOf(com.examprep.common.ValidationException.class)
                .hasMessageContaining("not ready");
    }

    @Test
    void sourceReferencesRejectsEmptySets() {
        SourceReferences references = SourceReferences.from(java.util.List.of());
        assertThat(references.resolveToLabel("c1")).isNull();
        assertThat(references.knownReferences()).isEmpty();
    }
}
