package com.examprep.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.examprep.ai.client.AiGateway;
import com.examprep.ai.client.AiPrompt;
import com.examprep.ai.exception.AiServiceException;
import com.examprep.api.DocumentApplicationService;
import com.examprep.api.StudyPackApplicationService;
import com.examprep.api.UserRegistrationService;
import com.examprep.api.dto.DocumentUploadResult;
import com.examprep.api.dto.ExamSessionDto;
import com.examprep.api.dto.PracticeAnswerDto;
import com.examprep.api.dto.QuizResultDto;
import com.examprep.api.dto.QuizSubmissionDto;
import com.examprep.api.dto.RegisterUserRequest;
import com.examprep.api.dto.StudyPackCreationResult;
import com.examprep.api.dto.StudyPackDto;
import com.examprep.api.dto.StudyPreferences;
import com.examprep.api.dto.TutorAnswerDto;
import com.examprep.api.dto.TutorMessageDto;
import com.examprep.api.dto.TutorQuestionDto;
import com.examprep.api.dto.UserAccountDto;
import com.examprep.api.model.DocumentStatus;
import com.examprep.api.model.QuestionType;
import com.examprep.api.model.StudyPackStatus;
import com.examprep.common.ValidationException;
import com.examprep.support.AbstractIntegrationTest;

class StudyWorkflowIntegrationTest extends AbstractIntegrationTest {

    private static final String PACKAGE_JSON = """
            {
              "title": "Microeconomics revision",
              "introduction": "A revision package on demand, supply and elasticity.",
              "summary": "The material explains how equilibrium prices form and how elasticity shapes responses.",
              "learningObjectives": ["Explain equilibrium", "Calculate elasticity implications"],
              "keyAreasToRevise": ["Elasticity", "Market equilibrium"],
              "topics": [
                {"name": "Elasticity", "explanation": "Elasticity measures responsiveness of quantity.",
                 "keyConcepts": ["price", "quantity"], "sources": ["c1"]}
              ],
              "revisionSections": [
                {"heading": "Demand and supply", "body": "Equilibrium occurs where supply meets demand.",
                 "sources": ["c1"]}
              ],
              "definitions": [
                {"term": "Elasticity", "definition": "Responsiveness of quantity to price.", "source": "c1"}
              ],
              "formulas": [
                {"name": "Total revenue", "expression": "R = P x Q", "explanation": "Revenue is price times quantity.",
                 "source": "c1"}
              ],
              "flashcards": [
                {"front": "What does elasticity measure?", "back": "Responsiveness to a price change.",
                 "source": "c1"}
              ],
              "questions": [
                {"type": "MULTIPLE_CHOICE", "question": "When demand rises and supply is fixed, what happens to price?",
                 "options": ["It rises", "It falls", "It stays the same"], "correctOptionIndex": 0,
                 "explanation": "Because the material says so.", "difficulty": "EASY", "source": "c1"},
                {"type": "MULTIPLE_CHOICE", "question": "Elastic demand means quantity changes...",
                 "options": ["A lot", "Very little"], "correctOptionIndex": 0,
                 "explanation": "Elastic means strongly responsive.", "difficulty": "MEDIUM", "source": "c1"},
                {"type": "TRUE_FALSE", "question": "Supply elasticity depends on production capacity.",
                 "correctAnswer": true, "explanation": "Spare capacity allows a faster response.",
                 "difficulty": "MEDIUM", "source": "c1"},
                {"type": "TRUE_FALSE", "question": "Price ceilings always increase supply.",
                 "correctAnswer": false, "explanation": "Ceilings can reduce supply.",
                 "difficulty": "HARD", "source": "c1"},
                {"type": "SHORT_ANSWER", "question": "Define elasticity.",
                 "suggestedAnswer": "A measure of how responsive quantity is to a price change.",
                 "explanation": "Focus on responsiveness.", "difficulty": "EASY", "source": "c1"}
              ]
            }
            """;

    private static final String TUTOR_JSON = """
            {"answer": "Elasticity measures how responsive quantity is to price changes. In your notes it is linked to substitutes and capacity.",
             "answeredFromMaterial": true, "sources": ["c1"]}
            """;

    private static final String STUDY_TEXT = """
            Demand and supply are the foundations of price formation in a market economy.

            When demand increases and supply is fixed, the equilibrium price rises. Elasticity measures how strongly
            quantity responds to a change in price, income or the price of a related good. Goods with close
            substitutes usually have more elastic demand. Producers with spare capacity can respond to higher
            prices, so their supply is more elastic in the short run.
            """;

    @MockitoBean
    private AiGateway aiGateway;

    @Autowired
    private DocumentApplicationService documentService;

    @Autowired
    private StudyPackApplicationService studyPackService;

    @Autowired
    private UserRegistrationService registrationService;

    @BeforeEach
    void mockAi() {
        when(aiGateway.generate(any(AiPrompt.class))).thenAnswer(invocation -> {
            AiPrompt prompt = invocation.getArgument(0);
            return prompt.userPrompt().contains("Student question:") ? TUTOR_JSON : PACKAGE_JSON;
        });
    }

    @Test
    void generationExamScoringTutorAndPagesWorkEndToEnd() throws Exception {
        UserAccountDto owner = register("Ivy");
        UUID documentId = uploadAndAwaitReady(owner);
        StudyPackDto pack = generateAndAwaitReady(owner, documentId);

        assertThat(pack.title()).isEqualTo("Microeconomics revision");
        assertThat(pack.topics()).hasSize(1);
        assertThat(pack.topics().get(0).sources()).containsExactly("Document text");
        assertThat(pack.revisionSections()).hasSize(1);
        assertThat(pack.definitions()).hasSize(1);
        assertThat(pack.formulas()).hasSize(1);
        assertThat(pack.flashcards()).hasSize(1);
        assertThat(pack.practiceQuestions()).hasSize(5);
        assertThat(pack.examQuestionCount()).isEqualTo(4);
        assertThat(pack.introduction()).contains("revision package");

        // The complete study workspace renders server-side.
        mockMvc.perform(get("/study/" + pack.id()).with(user(securityUser(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Microeconomics revision")))
                .andExpect(content().string(containsString("Learning objectives")));
        mockMvc.perform(get("/study/" + pack.id() + "?tab=topics").with(user(securityUser(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Elasticity")));
        mockMvc.perform(get("/study/" + pack.id() + "?tab=notes").with(user(securityUser(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Demand and supply")));
        mockMvc.perform(get("/study/" + pack.id() + "?tab=definitions").with(user(securityUser(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Responsiveness of quantity to price")));
        mockMvc.perform(get("/study/" + pack.id() + "?tab=formulas").with(user(securityUser(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("R = P x Q")));
        mockMvc.perform(get("/study/" + pack.id() + "/flashcards").with(user(securityUser(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("What does elasticity measure?")));
        mockMvc.perform(get("/study/" + pack.id() + "/practice").with(user(securityUser(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reveal answer")));
        mockMvc.perform(get("/study/" + pack.id() + "/tutor").with(user(securityUser(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ask about this material")));
        mockMvc.perform(get("/study/" + pack.id() + "/exam").with(user(securityUser(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Examination conditions")));

        // Exam pages must not leak answers before submission.
        ExamSessionDto exam = studyPackService.startExam(pack.id(), owner.id());
        assertThat(exam.questions()).hasSize(4);
        assertThat(exam.questions()).allMatch(question -> question.type() != QuestionType.SHORT_ANSWER);
        String examHtml = mockMvc.perform(get("/exam/" + exam.attemptId()).with(user(securityUser(owner))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(examHtml).contains("Submit examination");
        assertThat(examHtml).doesNotContain("Because the material says so");
        assertThat(examHtml).doesNotContain("Elastic means strongly responsive");

        List<QuizSubmissionDto.Answer> answers = exam.questions().stream()
                .map(question -> question.type() == QuestionType.MULTIPLE_CHOICE
                        ? new QuizSubmissionDto.Answer(question.questionId(), 0, null)
                        : new QuizSubmissionDto.Answer(question.questionId(), null, true))
                .toList();
        QuizResultDto result = studyPackService.submitExam(pack.id(), owner.id(),
                new QuizSubmissionDto(exam.attemptId(), 95, true, answers));
        assertThat(result.totalQuestions()).isEqualTo(4);
        assertThat(result.items()).hasSize(4);
        assertThat(result.score()).isEqualTo(3);
        assertThat(result.percentage()).isEqualTo(75);
        assertThat(result.revisionAreas()).isNotEmpty();

        mockMvc.perform(get("/exam/" + exam.attemptId() + "/results").with(user(securityUser(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Examination results")))
                .andExpect(content().string(containsString("75")))
                .andExpect(content().string(not(containsString("Submit examination"))));

        assertThatThrownBy(() -> studyPackService.getExamSession(exam.attemptId(), owner.id()))
                .isInstanceOf(ValidationException.class);

        assertThat(studyPackService.recentAttempts(owner.id(), 5)).hasSize(1);

        // Practice feedback is only returned when requested.
        PracticeAnswerDto reveal = studyPackService.revealPracticeAnswer(pack.id(),
                pack.practiceQuestions().get(0).id(), owner.id());
        assertThat(reveal.correctAnswer()).isNotBlank();

        // Tutor answer is grounded and persisted.
        TutorAnswerDto answer = studyPackService.askTutor(pack.id(), owner.id(),
                new TutorQuestionDto("What does elasticity measure?"));
        assertThat(answer.answer()).contains("Elasticity");
        assertThat(answer.sources()).containsExactly("Document text");
        assertThat(answer.answeredFromMaterial()).isTrue();
        List<TutorMessageDto> history = studyPackService.tutorHistory(pack.id(), owner.id());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).role()).isEqualTo("user");
        assertThat(history.get(1).role()).isEqualTo("assistant");

        studyPackService.recordFlashcardReview(pack.id(), owner.id(), 1, 1);

        mockMvc.perform(post("/api/study/" + pack.id() + "/flashcards/review")
                        .with(user(securityUser(owner))).with(csrf())
                        .contentType("application/json")
                        .content("{\"reviewed\":1,\"total\":1}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void studyPackagesAreIsolatedBetweenStudents() {
        UserAccountDto owner = register("Owner");
        UserAccountDto stranger = register("Stranger");
        StudyPackDto pack = generateAndAwaitReady(owner, uploadAndAwaitReady(owner));

        assertThatThrownBy(() -> studyPackService.getStudyPack(pack.id(), stranger.id()))
                .isInstanceOf(com.examprep.common.NotFoundException.class);
        assertThatThrownBy(() -> studyPackService.delete(pack.id(), stranger.id()))
                .isInstanceOf(com.examprep.common.NotFoundException.class);
        assertThatThrownBy(() -> studyPackService.startExam(pack.id(), stranger.id()))
                .isInstanceOf(com.examprep.common.NotFoundException.class);
        assertThatThrownBy(() -> studyPackService.tutorHistory(pack.id(), stranger.id()))
                .isInstanceOf(com.examprep.common.NotFoundException.class);
    }

    @Test
    void retryRecoversAFailedStudyPack() throws Exception {
        UserAccountDto owner = register("Retry");
        UUID documentId = uploadAndAwaitReady(owner);
        // The first generation fails; the retry must be able to recover it to READY.
        when(aiGateway.generate(any(AiPrompt.class)))
                .thenThrow(new AiServiceException("The AI router was unavailable."))
                .thenAnswer(invocation -> PACKAGE_JSON);

        StudyPackCreationResult creation = studyPackService.create(documentId, owner.id(),
                StudyPreferences.defaults());
        await(Duration.ofSeconds(20), () ->
                studyPackService.getStatus(creation.studyPackId(), owner.id()).status() == StudyPackStatus.FAILED);

        mockMvc.perform(post("/study/" + creation.studyPackId() + "/retry")
                        .with(user(securityUser(owner))).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/study/" + creation.studyPackId()));

        await(Duration.ofSeconds(30), () ->
                studyPackService.getStatus(creation.studyPackId(), owner.id()).status() == StudyPackStatus.READY);
        assertThat(studyPackService.getStudyPack(creation.studyPackId(), owner.id()).flashcards()).isNotEmpty();
    }

    @Test
    void failedTutorCallLeavesNoOrphanUserMessage() throws Exception {
        UserAccountDto owner = register("Tutor");
        StudyPackDto pack = generateAndAwaitReady(owner, uploadAndAwaitReady(owner));
        when(aiGateway.generate(any(AiPrompt.class)))
                .thenThrow(new AiServiceException("The AI router was unavailable."));

        mockMvc.perform(post("/api/study/" + pack.id() + "/tutor")
                        .with(user(securityUser(owner))).with(csrf())
                        .contentType("application/json")
                        .content("{\"message\":\"What does elasticity measure?\"}"))
                .andExpect(status().isBadGateway());

        // The question must not be persisted when the tutor never answered it.
        assertThat(studyPackService.tutorHistory(pack.id(), owner.id())).isEmpty();
    }

    @Test
    void resubmittingAnExamRedirectsToResultsInsteadOfFailing() throws Exception {
        UserAccountDto owner = register("Resubmit");
        StudyPackDto pack = generateAndAwaitReady(owner, uploadAndAwaitReady(owner));
        ExamSessionDto exam = studyPackService.startExam(pack.id(), owner.id());

        for (int submission = 0; submission < 2; submission++) {
            mockMvc.perform(post("/exam/" + exam.attemptId() + "/submit")
                            .with(user(securityUser(owner))).with(csrf())
                            .param("timeTaken", "30"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/exam/" + exam.attemptId() + "/results"));
        }
    }

    private StudyPackDto generateAndAwaitReady(UserAccountDto owner, UUID documentId) {
        StudyPackCreationResult creation = studyPackService.create(documentId, owner.id(),
                StudyPreferences.defaults());
        await(Duration.ofSeconds(30), () ->
                studyPackService.getStatus(creation.studyPackId(), owner.id()).status() == StudyPackStatus.READY);
        return studyPackService.getStudyPack(creation.studyPackId(), owner.id());
    }

    private UUID uploadAndAwaitReady(UserAccountDto owner) {
        MockMultipartFile file = new MockMultipartFile("file", "economics-notes.txt", "text/plain",
                STUDY_TEXT.getBytes(StandardCharsets.UTF_8));
        DocumentUploadResult upload = documentService.upload(file, owner.id());
        await(Duration.ofSeconds(20), () ->
                documentService.getStatus(upload.documentId(), owner.id()).status() == DocumentStatus.READY);
        return upload.documentId();
    }

    private UserAccountDto register(String name) {
        String email = name.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        return registrationService.register(new RegisterUserRequest(name, email, "study-password-1"));
    }
}
