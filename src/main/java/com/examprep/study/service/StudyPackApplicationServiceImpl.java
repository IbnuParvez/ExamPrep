package com.examprep.study.service;

import com.examprep.ai.retrieval.DocumentChunkRetriever;
import com.examprep.ai.service.TutorAnswerGenerator;
import com.examprep.api.document.DocumentApplicationService;
import com.examprep.api.document.DocumentChunkDto;
import com.examprep.api.document.DocumentContext;
import com.examprep.api.document.DocumentProcessingStatus;
import com.examprep.api.document.DocumentSummaryDto;
import com.examprep.api.study.Difficulty;
import com.examprep.api.study.ExamSummaryDto;
import com.examprep.api.study.FlashcardDto;
import com.examprep.api.study.PracticeAnswerDto;
import com.examprep.api.study.QuestionDto;
import com.examprep.api.study.QuizAnswerSubmission;
import com.examprep.api.study.QuizResultDto;
import com.examprep.api.study.QuizResultItemDto;
import com.examprep.api.study.QuizSubmissionDto;
import com.examprep.api.study.StudyPackApplicationService;
import com.examprep.api.study.StudyPackCreationResult;
import com.examprep.api.study.StudyPackDto;
import com.examprep.api.study.StudyPackStatus;
import com.examprep.api.study.StudyPackStatusDto;
import com.examprep.api.study.StudyPackSummaryDto;
import com.examprep.api.study.StudyPreferences;
import com.examprep.api.study.StudyProgressDto;
import com.examprep.api.study.TutorAnswerDto;
import com.examprep.api.study.TutorMessageDto;
import com.examprep.api.study.TutorQuestionDto;
import com.examprep.common.ResourceNotFoundException;
import com.examprep.common.ValidationException;
import com.examprep.config.AppProperties;
import com.examprep.study.domain.Flashcard;
import com.examprep.study.domain.FlashcardReview;
import com.examprep.study.domain.Question;
import com.examprep.study.domain.QuizAnswer;
import com.examprep.study.domain.QuizAttempt;
import com.examprep.study.domain.QuizMode;
import com.examprep.study.domain.StudyPack;
import com.examprep.study.domain.TutorMessage;
import com.examprep.study.repository.FlashcardRepository;
import com.examprep.study.repository.FlashcardReviewRepository;
import com.examprep.study.repository.QuestionRepository;
import com.examprep.study.repository.QuizAnswerRepository;
import com.examprep.study.repository.QuizAttemptRepository;
import com.examprep.study.repository.StudyPackRepository;
import com.examprep.study.repository.TutorMessageRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyPackApplicationServiceImpl implements StudyPackApplicationService {

    private static final Logger log = LoggerFactory.getLogger(StudyPackApplicationServiceImpl.class);

    private final StudyPackRepository studyPackRepository;
    private final FlashcardRepository flashcardRepository;
    private final QuestionRepository questionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAnswerRepository quizAnswerRepository;
    private final TutorMessageRepository tutorMessageRepository;
    private final FlashcardReviewRepository flashcardReviewRepository;
    private final DocumentApplicationService documentApplicationService;
    private final StudyPackGenerationWorker generationWorker;
    private final StudyPackStateWriter stateWriter;
    private final StudyPackMapper mapper;
    private final DocumentChunkRetriever chunkRetriever;
    private final TutorAnswerGenerator tutorAnswerGenerator;
    private final AppProperties properties;

    public StudyPackApplicationServiceImpl(StudyPackRepository studyPackRepository,
                                           FlashcardRepository flashcardRepository,
                                           QuestionRepository questionRepository,
                                           QuizAttemptRepository quizAttemptRepository,
                                           QuizAnswerRepository quizAnswerRepository,
                                           TutorMessageRepository tutorMessageRepository,
                                           FlashcardReviewRepository flashcardReviewRepository,
                                           DocumentApplicationService documentApplicationService,
                                           StudyPackGenerationWorker generationWorker,
                                           StudyPackStateWriter stateWriter,
                                           StudyPackMapper mapper,
                                           DocumentChunkRetriever chunkRetriever,
                                           TutorAnswerGenerator tutorAnswerGenerator,
                                           AppProperties properties) {
        this.studyPackRepository = studyPackRepository;
        this.flashcardRepository = flashcardRepository;
        this.questionRepository = questionRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.quizAnswerRepository = quizAnswerRepository;
        this.tutorMessageRepository = tutorMessageRepository;
        this.flashcardReviewRepository = flashcardReviewRepository;
        this.documentApplicationService = documentApplicationService;
        this.generationWorker = generationWorker;
        this.stateWriter = stateWriter;
        this.mapper = mapper;
        this.chunkRetriever = chunkRetriever;
        this.tutorAnswerGenerator = tutorAnswerGenerator;
        this.properties = properties;
    }

    @Override
    public StudyPackCreationResult create(UUID documentId, UUID ownerId, StudyPreferences preferences) {
        DocumentSummaryDto document = documentApplicationService.getDocument(documentId, ownerId);
        if (document.status() != DocumentProcessingStatus.READY) {
            throw new ValidationException(
                    "This document has not finished processing yet. Wait until extraction is complete and try again.");
        }
        StudyPreferences effective = preferences == null ? StudyPreferences.defaults() : preferences;
        AppProperties.Study settings = properties.study();
        int questionCount = clamp(effective.questionCount() == null
                ? settings.defaultQuestionCount() : effective.questionCount(), 1, settings.maxQuestionCount());
        int flashcardCount = clamp(effective.flashcardCount() == null
                ? settings.defaultFlashcardCount() : effective.flashcardCount(), 1, settings.maxFlashcardCount());
        int duration = clamp(effective.examDurationMinutes() == null
                ? settings.defaultExamDurationMinutes() : effective.examDurationMinutes(), 5, 180);
        String language = sanitize(effective.language(), "English", 20);
        String level = sanitize(effective.academicLevel(), "Undergraduate", 40);

        String title = document.originalFilename() == null ? "Study package"
                : stripExtension(document.originalFilename());
        StudyPack pack = StudyPack.queued(ownerId, documentId, title, language, level,
                effective.examDate(), questionCount, flashcardCount, duration);
        studyPackRepository.save(pack);

        try {
            generationWorker.generate(pack.getId());
        } catch (RejectedExecutionException rejected) {
            log.warn("AI queue is full; study package {} was not started", pack.getId());
            stateWriter.fail(pack.getId(),
                    "The study generation queue is currently full. Please try again in a moment.");
        }
        return new StudyPackCreationResult(pack.getId(), StudyPackStatus.QUEUED);
    }

    @Override
    @Transactional(readOnly = true)
    public StudyPackStatusDto getStatus(UUID studyPackId, UUID ownerId) {
        StudyPack pack = requireOwned(studyPackId, ownerId);
        return new StudyPackStatusDto(pack.getId(), pack.getStatus(), pack.getProgressMessage(),
                pack.getFailureReason(), pack.getCreatedAt(), pack.getUpdatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public StudyPackDto getStudyPack(UUID studyPackId, UUID ownerId) {
        StudyPack pack = requireOwned(studyPackId, ownerId);
        List<Flashcard> flashcards = flashcardRepository.findByStudyPackIdOrderByPositionAsc(studyPackId);
        List<Question> questions = questionRepository.findByStudyPackIdOrderByPositionAsc(studyPackId);
        String filename = documentFilename(pack);
        return mapper.toDto(pack, flashcards, questions, filename);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudyPackSummaryDto> listStudyPacks(UUID ownerId) {
        List<StudyPack> packs = studyPackRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
        Map<UUID, QuizAttempt> latestAttempts = latestAttemptsByPack(ownerId);
        List<StudyPackSummaryDto> summaries = new ArrayList<>(packs.size());
        for (StudyPack pack : packs) {
            StudyPackSummaryDto summary = mapper.toSummary(pack,
                    (int) flashcardRepository.countByStudyPackId(pack.getId()),
                    (int) questionRepository.countByStudyPackId(pack.getId()),
                    latestAttempts.get(pack.getId()));
            summaries.add(new StudyPackSummaryDto(summary.studyPackId(), summary.documentId(),
                    summary.title(), filenameOf(pack), summary.status(), summary.flashcardCount(),
                    summary.questionCount(), summary.latestScore(), summary.latestTotal(),
                    summary.createdAt()));
        }
        return summaries;
    }

    @Override
    @Transactional(readOnly = true)
    public StudyProgressDto getProgress(UUID ownerId) {
        int documentsReady = (int) documentApplicationService.listDocuments(ownerId).stream()
                .filter(document -> document.status() == DocumentProcessingStatus.READY)
                .count();
        int studyPackCount = (int) studyPackRepository.countByOwnerId(ownerId);
        List<QuizAttempt> attempts = quizAttemptRepository.findByStudentIdOrderBySubmittedAtDesc(ownerId);
        int average = attempts.isEmpty() ? 0 : (int) Math.round(attempts.stream()
                .mapToDouble(attempt -> attempt.getTotalQuestions() == 0 ? 0
                        : attempt.getScore() * 100.0 / attempt.getTotalQuestions())
                .average()
                .orElse(0));
        int flashcardsReviewed = (int) flashcardReviewRepository.countByStudentId(ownerId);

        List<StudyPack> recentPacks = studyPackRepository.findTop5ByOwnerIdOrderByCreatedAtDesc(ownerId);
        Map<UUID, QuizAttempt> latest = latestAttemptsByPack(ownerId);
        List<StudyPackSummaryDto> recentPackSummaries = new ArrayList<>(recentPacks.size());
        for (StudyPack pack : recentPacks) {
            StudyPackSummaryDto summary = mapper.toSummary(pack,
                    (int) flashcardRepository.countByStudyPackId(pack.getId()),
                    (int) questionRepository.countByStudyPackId(pack.getId()),
                    latest.get(pack.getId()));
            recentPackSummaries.add(new StudyPackSummaryDto(summary.studyPackId(), summary.documentId(),
                    summary.title(), filenameOf(pack), summary.status(), summary.flashcardCount(),
                    summary.questionCount(), summary.latestScore(), summary.latestTotal(),
                    summary.createdAt()));
        }

        List<ExamSummaryDto> recentExams = attempts.stream()
                .limit(5)
                .map(attempt -> mapper.toExamSummary(attempt, packTitle(attempt.getStudyPackId())))
                .toList();

        return new StudyProgressDto(documentsReady, studyPackCount, attempts.size(), flashcardsReviewed,
                average, recentPackSummaries, recentExams);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionDto> getExamQuestions(UUID studyPackId, UUID ownerId) {
        requireReady(studyPackId, ownerId);
        List<Question> questions = questionRepository
                .findByStudyPackIdAndExamEligibleTrueOrderByPositionAsc(studyPackId);
        if (questions.isEmpty()) {
            throw new ValidationException(
                    "This study package does not contain any automatically scored examination questions.");
        }
        return questions.stream().map(mapper::toQuestionDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionDto> getPracticeQuestions(UUID studyPackId, UUID ownerId) {
        requireReady(studyPackId, ownerId);
        return questionRepository.findByStudyPackIdOrderByPositionAsc(studyPackId).stream()
                .map(mapper::toQuestionDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlashcardDto> getFlashcards(UUID studyPackId, UUID ownerId) {
        requireReady(studyPackId, ownerId);
        return flashcardRepository.findByStudyPackIdOrderByPositionAsc(studyPackId).stream()
                .map(mapper::toFlashcardDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PracticeAnswerDto checkPracticeAnswer(UUID studyPackId, UUID ownerId, UUID questionId,
                                                 String answer) {
        requireReady(studyPackId, ownerId);
        Question question = questionRepository.findByIdAndStudyPackId(questionId, studyPackId)
                .orElseThrow(() -> ResourceNotFoundException.of("Question", questionId));
        if (!question.getType().supportsAutomaticScoring()) {
            return new PracticeAnswerDto(question.getId(), false, false, question.getCorrectAnswer(),
                    question.getExplanation(), question.getSourceReference());
        }
        boolean correct = answersMatch(question.getCorrectAnswer(), answer);
        return new PracticeAnswerDto(question.getId(), true, correct, question.getCorrectAnswer(),
                question.getExplanation(), question.getSourceReference());
    }

    @Override
    @Transactional
    public QuizResultDto submitExam(UUID studyPackId, UUID ownerId, QuizSubmissionDto submission) {
        StudyPack pack = requireReady(studyPackId, ownerId);
        List<Question> examQuestions = questionRepository
                .findByStudyPackIdAndExamEligibleTrueOrderByPositionAsc(studyPackId);
        if (examQuestions.isEmpty()) {
            throw new ValidationException(
                    "This study package does not contain any automatically scored examination questions.");
        }

        Map<UUID, String> submitted = new HashMap<>();
        for (QuizAnswerSubmission entry : submission.answers()) {
            if (entry != null && entry.questionId() != null) {
                submitted.put(entry.questionId(), entry.answer());
            }
        }

        List<QuizResultItemDto> items = new ArrayList<>(examQuestions.size());
        List<QuizAnswer> answers = new ArrayList<>(examQuestions.size());
        int score = 0;
        for (Question question : examQuestions) {
            String given = submitted.get(question.getId());
            boolean correct = answersMatch(question.getCorrectAnswer(), given);
            if (correct) {
                score++;
            }
            answers.add(new QuizAnswer(null, question.getId(), given, correct));
            items.add(new QuizResultItemDto(question.getId(), question.getPrompt(), question.getType(),
                    safeOptions(question), given, question.getCorrectAnswer(), correct,
                    question.getExplanation(), question.getSourceReference()));
        }

        int timeLimit = Math.max(60, pack.getExamDurationMinutes() * 60 * 3);
        int timeTaken = Math.max(0, Math.min(submission.timeTakenSeconds(), timeLimit));
        QuizAttempt attempt = new QuizAttempt(studyPackId, ownerId, QuizMode.MOCK_EXAM, score,
                examQuestions.size(), timeTaken);
        quizAttemptRepository.save(attempt);

        List<QuizAnswer> persisted = answers.stream()
                .map(answer -> new QuizAnswer(attempt.getId(), answer.getQuestionId(),
                        answer.getGivenAnswer(), answer.isCorrect()))
                .toList();
        quizAnswerRepository.saveAll(persisted);

        int percentage = examQuestions.isEmpty() ? 0 : (int) Math.round(score * 100.0 / examQuestions.size());
        return new QuizResultDto(attempt.getId(), studyPackId, pack.getTitle(), score,
                examQuestions.size(), percentage, timeTaken, attempt.getSubmittedAt(), items,
                mapper.revisionAreas(items));
    }

    @Override
    @Transactional(readOnly = true)
    public QuizResultDto getAttempt(UUID studyPackId, UUID ownerId, UUID attemptId) {
        StudyPack pack = requireOwned(studyPackId, ownerId);
        QuizAttempt attempt = quizAttemptRepository
                .findByIdAndStudyPackIdAndStudentId(attemptId, studyPackId, ownerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Attempt", attemptId));
        Map<UUID, QuizAnswer> answers = new HashMap<>();
        for (QuizAnswer answer : quizAnswerRepository.findByAttemptId(attempt.getId())) {
            answers.put(answer.getQuestionId(), answer);
        }
        List<QuizResultItemDto> items = new ArrayList<>();
        for (Question question : questionRepository.findByStudyPackIdAndExamEligibleTrueOrderByPositionAsc(studyPackId)) {
            QuizAnswer answer = answers.get(question.getId());
            if (answer == null) {
                continue;
            }
            items.add(new QuizResultItemDto(question.getId(), question.getPrompt(), question.getType(),
                    safeOptions(question), answer.getGivenAnswer(), question.getCorrectAnswer(),
                    answer.isCorrect(), question.getExplanation(), question.getSourceReference()));
        }
        int percentage = attempt.getTotalQuestions() == 0 ? 0
                : (int) Math.round(attempt.getScore() * 100.0 / attempt.getTotalQuestions());
        return new QuizResultDto(attempt.getId(), studyPackId, pack.getTitle(), attempt.getScore(),
                attempt.getTotalQuestions(), percentage, attempt.getTimeTakenSeconds(),
                attempt.getSubmittedAt(), items, mapper.revisionAreas(items));
    }

    @Override
    public TutorAnswerDto askTutor(UUID studyPackId, UUID ownerId, TutorQuestionDto question) {
        StudyPack pack = requireReady(studyPackId, ownerId);
        String questionText = question.question().trim();
        DocumentContext context = documentApplicationService.getContext(pack.getDocumentId(), ownerId);
        List<DocumentChunkDto> retrieved = chunkRetriever.retrieve(context.chunks(), questionText,
                properties.tutor().maxContextChunks());

        StringBuilder conversation = new StringBuilder();
        List<TutorMessage> history = tutorMessageRepository.findByStudyPackIdOrderByCreatedAtAsc(studyPackId);
        for (TutorMessage message : history.subList(Math.max(0, history.size() - 6), history.size())) {
            conversation.append(com.examprep.ai.prompt.PromptLibrary.tutorHistoryLine(
                            message.getRole().name(), truncate(message.getContent(), 700)))
                    .append('\n');
        }
        String packSummary = (pack.getTitle() == null ? "" : pack.getTitle() + "\n")
                + (pack.getSummary() == null ? "" : truncate(pack.getSummary(), 3000));

        TutorAnswerGenerator.TutorAnswer generated = tutorAnswerGenerator.answer(context, retrieved,
                packSummary, conversation.toString(), questionText);
        stateWriter.saveTutorExchange(studyPackId, ownerId, questionText, generated);

        return new TutorAnswerDto(generated.answer(), generated.references(),
                generated.insufficientInformation(), java.time.Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TutorMessageDto> getTutorConversation(UUID studyPackId, UUID ownerId) {
        requireOwned(studyPackId, ownerId);
        return tutorMessageRepository.findByStudyPackIdOrderByCreatedAtAsc(studyPackId).stream()
                .map(message -> new TutorMessageDto(message.getRole().name(), message.getContent(),
                        message.getSourceReferences() == null ? List.of() : message.getSourceReferences(),
                        message.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void recordFlashcardReview(UUID studyPackId, UUID ownerId, UUID flashcardId) {
        requireReady(studyPackId, ownerId);
        Flashcard flashcard = flashcardRepository.findByIdAndStudyPackId(flashcardId, studyPackId)
                .orElseThrow(() -> ResourceNotFoundException.of("Flashcard", flashcardId));
        flashcardReviewRepository.save(new FlashcardReview(studyPackId, ownerId, flashcard.getId()));
    }

    @Override
    @Transactional
    public void delete(UUID studyPackId, UUID ownerId) {
        StudyPack pack = requireOwned(studyPackId, ownerId);
        studyPackRepository.delete(pack);
        studyPackRepository.flush();
    }

    private StudyPack requireOwned(UUID studyPackId, UUID ownerId) {
        return studyPackRepository.findByIdAndOwnerId(studyPackId, ownerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Study package", studyPackId));
    }

    private StudyPack requireReady(UUID studyPackId, UUID ownerId) {
        StudyPack pack = requireOwned(studyPackId, ownerId);
        if (pack.getStatus() == StudyPackStatus.FAILED) {
            throw new ValidationException(
                    "This study package could not be generated. Generate a new package from the document.");
        }
        if (pack.getStatus() != StudyPackStatus.READY) {
            throw new ValidationException("This study package is still being generated.");
        }
        return pack;
    }

    private Map<UUID, QuizAttempt> latestAttemptsByPack(UUID ownerId) {
        Map<UUID, QuizAttempt> latest = new LinkedHashMap<>();
        for (QuizAttempt attempt : quizAttemptRepository.findByStudentIdOrderBySubmittedAtDesc(ownerId)) {
            latest.putIfAbsent(attempt.getStudyPackId(), attempt);
        }
        return latest;
    }

    private String documentFilename(StudyPack pack) {
        try {
            return documentApplicationService.getDocument(pack.getDocumentId(), pack.getOwnerId())
                    .originalFilename();
        } catch (ResourceNotFoundException missingDocument) {
            return null;
        }
    }

    private String filenameOf(StudyPack pack) {
        return documentFilename(pack);
    }

    private String packTitle(UUID studyPackId) {
        return studyPackRepository.findById(studyPackId).map(StudyPack::getTitle).orElse("Study package");
    }

    private static List<String> safeOptions(Question question) {
        return question.getOptions() == null ? List.of() : question.getOptions();
    }

    private static boolean answersMatch(String correctAnswer, String givenAnswer) {
        if (correctAnswer == null || givenAnswer == null) {
            return false;
        }
        return normalize(correctAnswer).equals(normalize(givenAnswer));
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static String sanitize(String value, String fallback, int limit) {
        String candidate = value == null || value.isBlank() ? fallback : value.trim();
        return candidate.length() <= limit ? candidate : candidate.substring(0, limit);
    }

    private static String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
