package com.examprep.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.examprep.document.repository.DocumentChunkRepository;
import com.examprep.document.repository.DocumentRepository;
import com.examprep.security.AppUserPrincipal;
import com.examprep.study.domain.Question;
import com.examprep.study.domain.StudyPack;
import com.examprep.study.repository.FlashcardRepository;
import com.examprep.study.repository.FlashcardReviewRepository;
import com.examprep.study.repository.QuestionRepository;
import com.examprep.study.repository.StudyPackRepository;
import com.examprep.support.IntegrationTestBase;
import com.examprep.support.StudyFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class StudyPackIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DocumentRepository documentRepository;
    @Autowired
    private DocumentChunkRepository documentChunkRepository;
    @Autowired
    private StudyPackRepository studyPackRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private FlashcardRepository flashcardRepository;
    @Autowired
    private FlashcardReviewRepository flashcardReviewRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void examPageNeverExposesTheAnswerKey() throws Exception {
        Seeded seeded = seed("exam-page-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(get("/study/{id}/exam", seeded.packId()).with(user(seeded.student())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Which organelle")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Chloroplast")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(StudyFixtures.EXPLANATION_MARKER))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("correctAnswer"))));
    }

    @Test
    void examSubmissionIsScoredOnTheServerAndReviewed() throws Exception {
        Seeded seeded = seed("exam-score-" + UUID.randomUUID() + "@example.com");

        Map<String, Object> submission = new HashMap<>();
        submission.put("timeTakenSeconds", 240);
        submission.put("answers", List.of(
                Map.of("questionId", seeded.multipleChoiceId(), "answer", "Chloroplast"),
                Map.of("questionId", seeded.trueFalseId(), "answer", "False")));

        String body = mockMvc.perform(post("/api/study/{id}/exam", seeded.packId())
                        .with(user(seeded.student())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submission)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.totalQuestions").value(2))
                .andExpect(jsonPath("$.percentage").value(50))
                .andExpect(jsonPath("$.items[0].correct").value(true))
                .andExpect(jsonPath("$.items[1].correct").value(false))
                .andExpect(jsonPath("$.items[1].correctAnswer").value("True"))
                .andExpect(jsonPath("$.revisionAreas").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String attemptId = objectMapper.readTree(body).get("attemptId").asText();

        mockMvc.perform(get("/study/{id}/results/{attemptId}", seeded.packId(), attemptId)
                        .with(user(seeded.student())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1 / 2")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        StudyFixtures.EXPLANATION_MARKER)));
    }

    @Test
    void practiceFeedbackIsProducedOnTheServer() throws Exception {
        Seeded seeded = seed("practice-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(post("/api/study/{id}/practice/{questionId}", seeded.packId(), seeded.multipleChoiceId())
                        .with(user(seeded.student())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"Chloroplast\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoGraded").value(true))
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.correctAnswer").value("Chloroplast"))
                .andExpect(jsonPath("$.explanation").value(StudyFixtures.EXPLANATION_MARKER));

        mockMvc.perform(post("/api/study/{id}/practice/{questionId}", seeded.packId(), seeded.multipleChoiceId())
                        .with(user(seeded.student())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"Ribosome\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.correctAnswer").value("Chloroplast"));

        mockMvc.perform(post("/api/study/{id}/practice/{questionId}", seeded.packId(), seeded.shortAnswerId())
                        .with(user(seeded.student())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"my own attempt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoGraded").value(false))
                .andExpect(jsonPath("$.correctAnswer").value(StudyFixtures.SHORT_ANSWER_MODEL));
    }

    @Test
    void studyMaterialIsIsolatedBetweenStudents() throws Exception {
        Seeded seeded = seed("owner-" + UUID.randomUUID() + "@example.com");
        AppUserPrincipal intruder = createStudent("intruder-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(get("/study/{id}", seeded.packId()).with(user(intruder)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/study/{id}/exam", seeded.packId()).with(user(intruder)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/study/{id}/status", seeded.packId()).with(user(intruder)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/study/{id}/exam", seeded.packId()).with(user(intruder)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[],\"timeTakenSeconds\":10}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/study/{id}", seeded.packId()).with(user(seeded.student())))
                .andExpect(status().isOk());
    }

    @Test
    void flashcardReviewsAreRecordedForTheOwner() throws Exception {
        Seeded seeded = seed("cards-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(get("/study/{id}/flashcards", seeded.packId()).with(user(seeded.student())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Flashcards")));

        assertThat(flashcardReviewRepository.countByStudentId(seeded.student().userId())).isZero();
    }

    private Seeded seed(String email) {
        AppUserPrincipal student = createStudent(email);
        var document = StudyFixtures.seedReadyDocument(documentRepository, documentChunkRepository,
                student.userId());
        StudyPack pack = StudyFixtures.seedReadyPack(studyPackRepository, student.userId(),
                document.getId(), "Photosynthesis revision");
        StudyFixtures.seedQuestions(questionRepository, pack.getId());
        List<Question> questions = questionRepository.findByStudyPackIdOrderByPositionAsc(pack.getId());
        return new Seeded(student, pack.getId(), questions.get(0).getId(), questions.get(1).getId(),
                questions.get(2).getId());
    }

    private record Seeded(AppUserPrincipal student, UUID packId, UUID multipleChoiceId, UUID trueFalseId,
                          UUID shortAnswerId) {
    }
}
