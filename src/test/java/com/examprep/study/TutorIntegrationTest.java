package com.examprep.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.examprep.ai.client.GeminiGateway;
import com.examprep.document.repository.DocumentChunkRepository;
import com.examprep.document.repository.DocumentRepository;
import com.examprep.security.AppUserPrincipal;
import com.examprep.study.domain.StudyPack;
import com.examprep.study.repository.StudyPackRepository;
import com.examprep.study.repository.TutorMessageRepository;
import com.examprep.support.IntegrationTestBase;
import com.examprep.support.StudyFixtures;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class TutorIntegrationTest extends IntegrationTestBase {

    @MockitoBean
    private GeminiGateway geminiGateway;

    @Autowired
    private DocumentRepository documentRepository;
    @Autowired
    private DocumentChunkRepository documentChunkRepository;
    @Autowired
    private StudyPackRepository studyPackRepository;
    @Autowired
    private TutorMessageRepository tutorMessageRepository;

    @Test
    void tutorAnswerKeepsOnlyRealCitationsAndPersistsTheConversation() throws Exception {
        AppUserPrincipal student = createStudent("tutor-" + UUID.randomUUID() + "@example.com");
        var document = StudyFixtures.seedReadyDocument(documentRepository, documentChunkRepository,
                student.userId());
        StudyPack pack = StudyFixtures.seedReadyPack(studyPackRepository, student.userId(),
                document.getId(), "Photosynthesis revision");

        when(geminiGateway.isConfigured()).thenReturn(true);
        when(geminiGateway.generateJson(any())).thenReturn("""
                {"answer":"Chlorophyll absorbs light energy in the chloroplast.",
                 "sourceRefs":["S1","S99"],"insufficientInformation":false}
                """);

        mockMvc.perform(post("/api/study/{id}/tutor", pack.getId())
                        .with(user(student)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What does chlorophyll do?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(
                        "Chlorophyll absorbs light energy in the chloroplast."))
                .andExpect(jsonPath("$.sourceReferences.length()").value(1))
                .andExpect(jsonPath("$.sourceReferences[0].referenceId").value("S1"))
                .andExpect(jsonPath("$.sourceReferences[0].locationLabel").value("Page 1"))
                .andExpect(jsonPath("$.insufficientInformation").value(false));

        assertThat(tutorMessageRepository.findByStudyPackIdOrderByCreatedAtAsc(pack.getId())).hasSize(2);

        mockMvc.perform(get("/study/{id}/tutor", pack.getId()).with(user(student)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Chlorophyll absorbs light energy")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("What does chlorophyll do?")));
    }

    @Test
    void tutorRejectsBlankQuestions() throws Exception {
        AppUserPrincipal student = createStudent("tutor-blank-" + UUID.randomUUID() + "@example.com");
        var document = StudyFixtures.seedReadyDocument(documentRepository, documentChunkRepository,
                student.userId());
        StudyPack pack = StudyFixtures.seedReadyPack(studyPackRepository, student.userId(),
                document.getId(), "Photosynthesis revision");

        mockMvc.perform(post("/api/study/{id}/tutor", pack.getId())
                        .with(user(student)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest());

        assertThat(tutorMessageRepository.countByStudyPackId(pack.getId())).isZero();
    }
}
