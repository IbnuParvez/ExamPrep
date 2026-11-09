package com.examprep.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.examprep.document.repository.DocumentChunkRepository;
import com.examprep.document.repository.DocumentRepository;
import com.examprep.security.AppUserPrincipal;
import com.examprep.study.domain.Question;
import com.examprep.study.domain.StudyPack;
import com.examprep.study.repository.QuestionRepository;
import com.examprep.study.repository.StudyPackRepository;
import com.examprep.support.IntegrationTestBase;
import com.examprep.support.StudyFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Renders every authenticated page once so template or view-model mistakes fail the build.
 */
class AuthenticatedPageSmokeTest extends IntegrationTestBase {

    @Autowired
    private DocumentRepository documentRepository;
    @Autowired
    private DocumentChunkRepository documentChunkRepository;
    @Autowired
    private StudyPackRepository studyPackRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void everyAuthenticatedPageRenders() throws Exception {
        AppUserPrincipal student = createStudent("smoke-" + UUID.randomUUID() + "@example.com");
        var document = StudyFixtures.seedReadyDocument(documentRepository, documentChunkRepository,
                student.userId());
        StudyPack pack = StudyFixtures.seedReadyPack(studyPackRepository, student.userId(),
                document.getId(), "Photosynthesis revision");
        StudyFixtures.seedQuestions(questionRepository, pack.getId());
        List<Question> questions = questionRepository.findByStudyPackIdOrderByPositionAsc(pack.getId());

        mockMvc.perform(get("/dashboard").with(user(student)))
                .andExpect(status().isOk()).andExpect(view().name("dashboard/index"));
        mockMvc.perform(get("/documents").with(user(student)))
                .andExpect(status().isOk()).andExpect(view().name("documents/index"));
        mockMvc.perform(get("/documents/{id}", document.getId()).with(user(student)))
                .andExpect(status().isOk()).andExpect(view().name("documents/detail"));
        mockMvc.perform(get("/study").with(user(student)))
                .andExpect(status().isOk()).andExpect(view().name("study/index"));

        mockMvc.perform(get("/study/{id}", pack.getId()).with(user(student)))
                .andExpect(status().isOk()).andExpect(view().name("study/overview"));
        mockMvc.perform(get("/study/{id}/topics", pack.getId()).with(user(student)))
                .andExpect(status().isOk()).andExpect(view().name("study/topics"));
        mockMvc.perform(get("/study/{id}/notes", pack.getId()).with(user(student)))
                .andExpect(status().isOk()).andExpect(view().name("study/notes"));
        mockMvc.perform(get("/study/{id}/definitions", pack.getId()).with(user(student)))
                .andExpect(status().isOk()).andExpect(view().name("study/definitions"));
        mockMvc.perform(get("/study/{id}/formulas", pack.getId()).with(user(student)))
                .andExpect(status().isOk()).andExpect(view().name("study/formulas"));
        mockMvc.perform(get("/study/{id}/flashcards", pack.getId()).with(user(student)))
                .andExpect(status().isOk()).andExpect(view().name("study/flashcards"));
        mockMvc.perform(get("/study/{id}/practice", pack.getId()).with(user(student)))
                .andExpect(status().isOk()).andExpect(view().name("study/practice"));
        mockMvc.perform(get("/study/{id}/tutor", pack.getId()).with(user(student)))
                .andExpect(status().isOk()).andExpect(view().name("study/tutor"));
        mockMvc.perform(get("/study/{id}/exam", pack.getId()).with(user(student)))
                .andExpect(status().isOk()).andExpect(view().name("study/exam"));

        String body = mockMvc.perform(post("/api/study/{id}/exam", pack.getId())
                        .with(user(student)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "timeTakenSeconds", 90,
                                "answers", List.of(Map.of("questionId", questions.get(0).getId(),
                                        "answer", "Chloroplast"))))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String attemptId = objectMapper.readTree(body).get("attemptId").asText();

        mockMvc.perform(get("/study/{id}/results/{attemptId}", pack.getId(), attemptId).with(user(student)))
                .andExpect(status().isOk()).andExpect(view().name("study/results"));
    }

    @Test
    void pendingStudyPackRendersTheStatusPage() throws Exception {
        AppUserPrincipal student = createStudent("smoke-pending-" + UUID.randomUUID() + "@example.com");
        var document = StudyFixtures.seedReadyDocument(documentRepository, documentChunkRepository,
                student.userId());
        StudyPack queued = studyPackRepository.save(StudyPack.queued(student.userId(), document.getId(),
                "Pending revision", "English", "Undergraduate", null, 4, 4, 20));

        String body = mockMvc.perform(get("/study/{id}", queued.getId()).with(user(student)))
                .andExpect(status().isOk())
                .andExpect(view().name("study/status"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).containsIgnoringCase("queued");
    }
}
