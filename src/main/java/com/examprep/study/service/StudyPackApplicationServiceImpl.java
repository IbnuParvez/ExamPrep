package com.examprep.study.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.examprep.ai.service.StudyGenerationService;
import com.examprep.api.DocumentApplicationService;
import com.examprep.api.StudyPackApplicationService;
import com.examprep.api.dto.DocumentStatusDto;
import com.examprep.api.dto.ExamSessionDto;
import com.examprep.api.dto.PracticeAnswerDto;
import com.examprep.api.dto.QuizAttemptSummaryDto;
import com.examprep.api.dto.QuizResultDto;
import com.examprep.api.dto.QuizSubmissionDto;
import com.examprep.api.dto.StudyPackCreationResult;
import com.examprep.api.dto.StudyPackDto;
import com.examprep.api.dto.StudyPackStatusDto;
import com.examprep.api.dto.StudyPackSummaryDto;
import com.examprep.api.dto.StudyPreferences;
import com.examprep.api.dto.StudyStatsDto;
import com.examprep.api.dto.TutorAnswerDto;
import com.examprep.api.dto.TutorMessageDto;
import com.examprep.api.dto.TutorQuestionDto;
import com.examprep.api.model.DocumentStatus;
import com.examprep.api.model.QuestionType;
import com.examprep.api.model.StudyPackStatus;
import com.examprep.common.AppException;
import com.examprep.common.NotFoundException;
import com.examprep.common.ValidationException;
import com.examprep.study.domain.AttemptStatus;
import com.examprep.study.domain.FlashcardEntity;
import com.examprep.study.domain.FlashcardReview;
import com.examprep.study.domain.QuestionEntity;
import com.examprep.study.domain.QuizAttempt;
import com.examprep.study.domain.QuizAttemptAnswer;
import com.examprep.study.domain.StudyPackageContent;
import com.examprep.study.domain.StudyPack;
import com.examprep.study.domain.TutorMessageEntity;
import com.examprep.study.repository.FlashcardRepository;
import com.examprep.study.repository.FlashcardReviewRepository;
import com.examprep.study.repository.QuestionRepository;
import com.examprep.study.repository.QuizAttemptAnswerRepository;
import com.examprep.study.repository.QuizAttemptRepository;
import com.examprep.study.repository.StudyPackRepository;
import com.examprep.study.repository.TutorMessageRepository;
import com.examprep.study.tutor.TutorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class StudyPackApplicationServiceImpl implements StudyPackApplicationService {

    private static final Logger log = LoggerFactory.getLogger(StudyPackApplicationServiceImpl.class);
    private static final int MIN_EXAM_QUESTIONS = 4;
    private static final int DEFAULT_EXAM_QUESTIONS = 10;
    private static final int DEFAULT_EXAM_MINUTES = 15;
    /** Extra time allowed between the last answer and submission, for slow networks and auto-submit. */
    private static final int SUBMIT_GRACE_SECONDS = 300;

    private final StudyPackRepository studyPackRepository;
    private final FlashcardRepository flashcardRepository;
    private final QuestionRepository questionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizAttemptAnswerRepository attemptAnswerRepository;
    private final TutorMessageRepository tutorMessageRepository;
    private final FlashcardReviewRepository flashcardReviewRepository;
    private final DocumentApplicationService documentService;
    private final StudyGenerationService generationService;
    private final TutorService tutorService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public StudyPackApplicationServiceImpl(StudyPackRepository studyPackRepository,
                                           FlashcardRepository flashcardRepository,
                                           QuestionRepository questionRepository,
                                           QuizAttemptRepository attemptRepository,
                                           QuizAttemptAnswerRepository attemptAnswerRepository,
                                           TutorMessageRepository tutorMessageRepository,
                                           FlashcardReviewRepository flashcardReviewRepository,
                                           DocumentApplicationService documentService,
                                           StudyGenerationService generationService,
                                           TutorService tutorService,
                                           ObjectMapper objectMapper,
                                           PlatformTransactionManager transactionManager) {
        this.studyPackRepository = studyPackRepository;
        this.flashcardRepository = flashcardRepository;
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.tutorMessageRepository = tutorMessageRepository;
        this.flashcardReviewRepository = flashcardReviewRepository;
        this.documentService = documentService;
        this.generationService = generationService;
        this.tutorService = tutorService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public StudyPackCreationResult create(UUID documentId, UUID ownerId, StudyPreferences preferences) {
        // getContext validates ownership and that extraction has finished.
        var context = documentService.getContext(documentId, ownerId);
        StudyPreferences safe = normalise(preferences);
        StudyPack pack = StudyPack.queued(ownerId, documentId, context.title(), safe);
        studyPackRepository.saveAndFlush(pack);
        try {
            generationService.generate(pack.getId());
        } catch (TaskRejectedException exception) {
            log.warn("Study generation queue is full; package {} was not scheduled", pack.getId());
            pack.markFailed("The AI generation queue is busy right now. Delete this package and try again.");
            studyPackRepository.save(pack);
        }
        return new StudyPackCreationResult(pack.getId(), pack.getStatus());
    }

    @Override
    public StudyPackStatusDto getStatus(UUID studyPackId, UUID ownerId) {
        StudyPack pack = requireOwnedPack(studyPackId, ownerId);
        return new StudyPackStatusDto(pack.getId(), pack.getStatus(), pack.getProgressMessage(),
                safeFailure(pack), pack.getProgressPercent());
    }

    @Override
    public StudyPackStatusDto retryGeneration(UUID studyPackId, UUID ownerId) {
        StudyPack pack = requireOwnedPack(studyPackId, ownerId);
        if (pack.getStatus() != StudyPackStatus.FAILED) {
            throw new ValidationException("Only a study package that failed can be generated again.");
        }
        // The source text must still be extractable; retrying after its document was deleted or failed
        // would queue a job that can only fail again.
        DocumentStatusDto document = documentService.getStatus(pack.getDocumentId(), ownerId);
        if (document.status() != DocumentStatus.READY) {
            throw new ValidationException("The source document is not ready, so this package cannot be "
                    + "generated again.");
        }
        // All preferences are stored on the pack, so a retry does not need the student to re-enter them.
        pack.resetForRetry();
        studyPackRepository.saveAndFlush(pack);
        try {
            generationService.generate(pack.getId());
        } catch (TaskRejectedException exception) {
            log.warn("Study generation queue is full; retry of package {} was not scheduled", pack.getId());
            pack.markFailed("The AI generation queue is busy right now. Please try again.");
            studyPackRepository.save(pack);
        }
        return new StudyPackStatusDto(pack.getId(), pack.getStatus(), pack.getProgressMessage(),
                safeFailure(pack), pack.getProgressPercent());
    }

    @Override
    public StudyPackDto getStudyPack(UUID studyPackId, UUID ownerId) {
        StudyPack pack = requireOwnedPack(studyPackId, ownerId);
        return toDto(pack, ownerId);
    }

    @Override
    public List<StudyPackSummaryDto> listStudyPacks(UUID ownerId) {
        return studyPackRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(pack -> new StudyPackSummaryDto(pack.getId(), pack.getDocumentId(), pack.getTitle(),
                        documentName(pack, ownerId),
                        pack.getStatus(), (int) flashcardRepository.countByStudyPackId(pack.getId()),
                        (int) questionRepository.countByStudyPackId(pack.getId()), pack.getCreatedAt()))
                .toList();
    }

    @Override
    public void delete(UUID studyPackId, UUID ownerId) {
        StudyPack pack = requireOwnedPack(studyPackId, ownerId);
        studyPackRepository.delete(pack);
    }

    @Override
    @Transactional
    public ExamSessionDto startExam(UUID studyPackId, UUID ownerId) {
        StudyPack pack = requireReadyPack(studyPackId, ownerId);
        List<QuestionEntity> pool = new ArrayList<>(
                questionRepository.findByStudyPackIdAndExamEligibleTrueOrderByOrdinalAsc(studyPackId));
        if (pool.size() < MIN_EXAM_QUESTIONS) {
            throw new ValidationException("This study package does not have enough multiple-choice or true/false "
                    + "questions for a mock examination yet.");
        }
        Collections.shuffle(pool);
        int requested = pack.getExamQuestionCount() == null ? DEFAULT_EXAM_QUESTIONS : pack.getExamQuestionCount();
        int count = Math.min(pool.size(), Math.max(MIN_EXAM_QUESTIONS, requested));
        List<QuestionEntity> selected = pool.subList(0, count);
        int durationSeconds = Math.max(300, (pack.getExamDurationMinutes() == null
                ? DEFAULT_EXAM_MINUTES : pack.getExamDurationMinutes()) * 60);

        QuizAttempt attempt = QuizAttempt.start(ownerId, studyPackId, selected.size(), durationSeconds);
        attemptRepository.saveAndFlush(attempt);

        List<QuizAttemptAnswer> answers = new ArrayList<>();
        int position = 1;
        for (QuestionEntity question : selected) {
            answers.add(QuizAttemptAnswer.pending(attempt.getId(), question.getId(), position++));
        }
        attemptAnswerRepository.saveAll(answers);
        return session(attempt, selected);
    }

    @Override
    public ExamSessionDto getExamSession(UUID attemptId, UUID ownerId) {
        QuizAttempt attempt = requireOwnedAttempt(attemptId, ownerId);
        if (attempt.getStatus() == AttemptStatus.SUBMITTED) {
            throw new ValidationException("This examination has already been submitted.");
        }
        List<QuizAttemptAnswer> rows = attemptAnswerRepository.findByAttemptIdOrderByPositionAsc(attempt.getId());
        Map<UUID, QuestionEntity> questions = loadQuestions(rows);
        List<QuestionEntity> ordered = rows.stream()
                .map(row -> questions.get(row.getQuestionId()))
                .filter(Objects::nonNull)
                .toList();
        long elapsed = Duration.between(attempt.getStartedAt(), Instant.now()).getSeconds();
        return buildSession(attempt, ordered, (int) Math.max(0, elapsed));
    }

    @Override
    @Transactional
    public QuizResultDto submitExam(UUID studyPackId, UUID ownerId, QuizSubmissionDto submission) {
        requireOwnedPack(studyPackId, ownerId);
        QuizAttempt attempt = requireOwnedAttempt(submission.attemptId(), ownerId);
        if (!attempt.getStudyPackId().equals(studyPackId)) {
            throw new NotFoundException("That examination could not be found.");
        }
        return gradeAndSubmit(attempt, ownerId, submission);
    }

    @Override
    @Transactional
    public QuizResultDto submitExamAttempt(UUID attemptId, UUID ownerId, QuizSubmissionDto submission) {
        return gradeAndSubmit(requireOwnedAttempt(attemptId, ownerId), ownerId, submission);
    }

    private QuizResultDto gradeAndSubmit(QuizAttempt attempt, UUID ownerId, QuizSubmissionDto submission) {
        if (attempt.getStatus() == AttemptStatus.SUBMITTED) {
            // Idempotent: a double-click or a timer race must not turn into an error page.
            return buildResult(attempt);
        }
        List<QuizAttemptAnswer> rows = attemptAnswerRepository.findByAttemptIdOrderByPositionAsc(attempt.getId());
        Map<UUID, QuestionEntity> questions = loadQuestions(rows);
        Map<UUID, QuizSubmissionDto.Answer> submitted = submission.answers() == null ? Map.of()
                : submission.answers().stream()
                .filter(answer -> answer != null && answer.questionId() != null)
                .collect(Collectors.toMap(QuizSubmissionDto.Answer::questionId, Function.identity(),
                        (first, second) -> first));

        int score = 0;
        for (QuizAttemptAnswer row : rows) {
            QuestionEntity question = questions.get(row.getQuestionId());
            QuizSubmissionDto.Answer answer = submitted.get(row.getQuestionId());
            boolean correct = false;
            if (question != null && answer != null) {
                if (question.getType() == QuestionType.MULTIPLE_CHOICE) {
                    correct = answer.selectedOptionIndex() != null
                            && answer.selectedOptionIndex().equals(question.getCorrectOptionIndex());
                    row.record(answer.selectedOptionIndex(), null, null, correct);
                } else if (question.getType() == QuestionType.TRUE_FALSE) {
                    correct = answer.booleanAnswer() != null
                            && answer.booleanAnswer().equals(question.getCorrectBoolean());
                    row.record(null, answer.booleanAnswer(), null, correct);
                } else {
                    row.record(null, null, answer.selectedOptionIndex() == null ? null : "", false);
                }
            } else if (question != null && question.getType() != QuestionType.SHORT_ANSWER) {
                row.record(null, null, null, false);
            }
            if (correct) {
                score++;
            }
        }
        attemptAnswerRepository.saveAll(rows);
        // Trust the server clock, not the hidden form field: the browser value is spoofable and is lost
        // when the exam page is reloaded.
        int serverElapsed = (int) Math.max(0,
                Duration.between(attempt.getStartedAt(), Instant.now()).getSeconds());
        int reported = Math.max(0, submission.timeTakenSeconds());
        int timeTaken = Math.min(attempt.getDurationSeconds() + SUBMIT_GRACE_SECONDS,
                Math.max(serverElapsed, reported));
        attempt.submit(score, timeTaken);
        attemptRepository.save(attempt);
        return buildResult(attempt);
    }

    @Override
    public QuizResultDto getResult(UUID attemptId, UUID ownerId) {
        QuizAttempt attempt = requireOwnedAttempt(attemptId, ownerId);
        if (attempt.getStatus() != AttemptStatus.SUBMITTED) {
            throw new ValidationException("This examination has not been submitted yet.");
        }
        return buildResult(attempt);
    }

    @Override
    public List<QuizAttemptSummaryDto> recentAttempts(UUID ownerId, int limit) {
        int size = Math.max(1, Math.min(limit, 20));
        List<QuizAttempt> attempts = attemptRepository.findByOwnerIdAndStatusOrderBySubmittedAtDesc(
                ownerId, AttemptStatus.SUBMITTED, PageRequest.of(0, size));
        Set<UUID> packIds = attempts.stream().map(QuizAttempt::getStudyPackId).collect(Collectors.toSet());
        Map<UUID, String> titles = studyPackRepository.findAllById(packIds).stream()
                .collect(Collectors.toMap(StudyPack::getId, StudyPack::getTitle));
        return attempts.stream()
                .map(attempt -> new QuizAttemptSummaryDto(
                        attempt.getId(),
                        attempt.getStudyPackId(),
                        titles.getOrDefault(attempt.getStudyPackId(), "Study package"),
                        attempt.getScore(),
                        attempt.getTotalQuestions(),
                        percentage(attempt.getScore(), attempt.getTotalQuestions()),
                        attempt.getTimeTakenSeconds() == null ? 0 : attempt.getTimeTakenSeconds(),
                        attempt.getSubmittedAt()))
                .toList();
    }

    @Override
    public StudyStatsDto statistics(UUID ownerId) {
        // Counting a 10-item page made "Mock exams taken" freeze at 10 for every active student, and
        // ignored every earlier score.
        int attempts = (int) attemptRepository.countByOwnerIdAndStatus(ownerId, AttemptStatus.SUBMITTED);
        if (attempts == 0) {
            return new StudyStatsDto(0, 0, 0);
        }
        int best = (int) Math.round(attemptRepository.maxPercentage(ownerId, AttemptStatus.SUBMITTED));
        int average = (int) Math.round(attemptRepository.averagePercentage(ownerId, AttemptStatus.SUBMITTED));
        return new StudyStatsDto(attempts, best, average);
    }

    @Override
    public PracticeAnswerDto revealPracticeAnswer(UUID studyPackId, UUID questionId, UUID ownerId) {
        requireOwnedPack(studyPackId, ownerId);
        QuestionEntity question = questionRepository.findByIdAndStudyPackId(questionId, studyPackId)
                .orElseThrow(() -> new NotFoundException("That question could not be found."));
        return new PracticeAnswerDto(question.getId(), question.getType(), question.correctAnswerText(),
                question.getExplanation(), question.getSourceRef());
    }

    @Override
    @Transactional
    public void recordFlashcardReview(UUID studyPackId, UUID ownerId, int reviewedCards, int totalCards) {
        requireOwnedPack(studyPackId, ownerId);
        int total = (int) flashcardRepository.countByStudyPackId(studyPackId);
        if (total == 0) {
            throw new ValidationException("This study package has no flashcards yet.");
        }
        int reviewed = Math.max(0, Math.min(reviewedCards, total));
        flashcardReviewRepository.save(FlashcardReview.of(ownerId, studyPackId, reviewed, total));
    }

    @Override
    public TutorAnswerDto askTutor(UUID studyPackId, UUID ownerId, TutorQuestionDto question) {
        StudyPack pack = requireReadyPack(studyPackId, ownerId);
        String message = question.message() == null ? "" : question.message().strip();
        if (message.isBlank()) {
            throw new ValidationException("Type a question for the tutor.");
        }
        // Pass ids rather than a DTO: building the DTO runs several extra queries (flashcards, questions
        // and document status) that the tutor does not need.
        TutorAnswerDto answer = tutorService.answer(ownerId, pack.getId(), pack.getDocumentId(), message);
        // Persist the exchange only once the model has answered, atomically, so a failure never leaves an
        // orphan student message. The assistant timestamp is forced after the question so the ascending
        // history order stays deterministic even when the clock resolves both writes to the same instant.
        Instant askedAt = Instant.now();
        transactionTemplate.executeWithoutResult(status -> {
            tutorMessageRepository.save(TutorMessageEntity.user(ownerId, studyPackId, message, askedAt));
            tutorMessageRepository.save(TutorMessageEntity.assistant(ownerId, studyPackId, answer.answer(),
                    answer.sources(), askedAt.plusMillis(1)));
        });
        return answer;
    }

    @Override
    public List<TutorMessageDto> tutorHistory(UUID studyPackId, UUID ownerId) {
        requireOwnedPack(studyPackId, ownerId);
        return tutorMessageRepository.findByStudyPackIdOrderByCreatedAtAsc(studyPackId).stream()
                .map(message -> new TutorMessageDto(message.getRole().name().toLowerCase(),
                        message.getContent(),
                        message.getSourceRefs() == null ? List.of() : message.getSourceRefs(),
                        message.getCreatedAt()))
                .toList();
    }

    private ExamSessionDto session(QuizAttempt attempt, List<QuestionEntity> questions) {
        return buildSession(attempt, questions, 0);
    }

    private ExamSessionDto buildSession(QuizAttempt attempt, List<QuestionEntity> questions, int elapsedSeconds) {
        StudyPack pack = studyPackRepository.findById(attempt.getStudyPackId()).orElse(null);
        List<ExamSessionDto.Question> items = new ArrayList<>();
        int position = 1;
        for (QuestionEntity question : questions) {
            items.add(new ExamSessionDto.Question(question.getId(), position++, question.getType(),
                    question.getQuestionText(),
                    question.getOptions() == null ? List.of() : question.getOptions()));
        }
        return new ExamSessionDto(attempt.getId(), attempt.getStudyPackId(),
                pack == null ? "Mock examination" : pack.getTitle(), attempt.getDurationSeconds(),
                elapsedSeconds, attempt.getStartedAt(), items);
    }

    private QuizResultDto buildResult(QuizAttempt attempt) {
        StudyPack pack = requirePack(attempt.getStudyPackId());
        List<QuizAttemptAnswer> rows = attemptAnswerRepository.findByAttemptIdOrderByPositionAsc(attempt.getId());
        Map<UUID, QuestionEntity> questions = loadQuestions(rows);
        List<QuizResultDto.Item> items = new ArrayList<>();
        Set<String> revisionAreas = new LinkedHashSet<>();
        for (QuizAttemptAnswer row : rows) {
            QuestionEntity question = questions.get(row.getQuestionId());
            if (question == null) {
                continue;
            }
            if (!row.isCorrect() && question.getSourceRef() != null) {
                revisionAreas.add(question.getSourceRef());
            }
            items.add(new QuizResultDto.Item(row.getPosition(), question.getQuestionText(), question.getType(),
                    studentAnswer(row, question), question.correctAnswerText(), row.isCorrect(),
                    question.getExplanation(), question.getSourceRef()));
        }
        return new QuizResultDto(attempt.getId(), attempt.getStudyPackId(), pack.getTitle(), attempt.getScore(),
                attempt.getTotalQuestions(), percentage(attempt.getScore(), attempt.getTotalQuestions()),
                attempt.getTimeTakenSeconds() == null ? 0 : attempt.getTimeTakenSeconds(),
                attempt.getSubmittedAt(), items, new ArrayList<>(revisionAreas));
    }

    private String studentAnswer(QuizAttemptAnswer row, QuestionEntity question) {
        if (question.getType() == QuestionType.MULTIPLE_CHOICE) {
            Integer index = row.getSelectedOptionIndex();
            if (index == null || question.getOptions() == null || index < 0
                    || index >= question.getOptions().size()) {
                return "No answer";
            }
            return question.getOptions().get(index);
        }
        if (question.getType() == QuestionType.TRUE_FALSE) {
            if (row.getBooleanAnswer() == null) {
                return "No answer";
            }
            return row.getBooleanAnswer() ? "True" : "False";
        }
        return row.getTextAnswer() == null || row.getTextAnswer().isBlank() ? "No answer" : row.getTextAnswer();
    }

    private Map<UUID, QuestionEntity> loadQuestions(List<QuizAttemptAnswer> rows) {
        List<UUID> ids = rows.stream().map(QuizAttemptAnswer::getQuestionId).toList();
        return questionRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(QuestionEntity::getId, Function.identity()));
    }

    private StudyPackDto toDto(StudyPack pack, UUID ownerId) {
        StudyPackageContent content = readContent(pack.getContent());
        List<StudyPackDto.Flashcard> flashcards = flashcardRepository
                .findByStudyPackIdOrderByOrdinalAsc(pack.getId()).stream()
                .map(this::toFlashcard)
                .toList();
        List<StudyPackDto.PracticeQuestion> practiceQuestions = questionRepository
                .findByStudyPackIdOrderByOrdinalAsc(pack.getId()).stream()
                .map(this::toPracticeQuestion)
                .toList();
        return new StudyPackDto(
                pack.getId(),
                pack.getDocumentId(),
                documentName(pack, ownerId),
                pack.getTitle(),
                pack.getIntroduction() == null ? "" : pack.getIntroduction(),
                pack.getSummary() == null ? "" : pack.getSummary(),
                pack.getObjectives() == null ? List.of() : pack.getObjectives(),
                pack.getRevisionAreas() == null ? List.of() : pack.getRevisionAreas(),
                content.topics(),
                content.revisionSections(),
                content.definitions(),
                content.formulas(),
                flashcards,
                practiceQuestions,
                examLength(pack),
                pack.getExamDurationMinutes() == null ? DEFAULT_EXAM_MINUTES : pack.getExamDurationMinutes(),
                pack.getStatus(),
                pack.getLanguage(),
                pack.getAcademicLevel(),
                pack.getCreatedAt());
    }

    /**
     * The number of questions the mock examination will actually contain, which startExam() derives from
     * the eligible pool and the configured length. Reporting the whole pool made the exam introduction
     * promise more questions than the attempt contained.
     */
    private int examLength(StudyPack pack) {
        int eligible = (int) questionRepository.countByStudyPackIdAndExamEligibleTrue(pack.getId());
        int configured = pack.getExamQuestionCount() == null ? DEFAULT_EXAM_QUESTIONS : pack.getExamQuestionCount();
        return Math.min(eligible, Math.max(MIN_EXAM_QUESTIONS, configured));
    }

    private StudyPackDto.Flashcard toFlashcard(FlashcardEntity entity) {
        return new StudyPackDto.Flashcard(entity.getId(), entity.getOrdinal(), entity.getFront(), entity.getBack(),
                entity.getSourceRef());
    }

    private StudyPackDto.PracticeQuestion toPracticeQuestion(QuestionEntity entity) {
        return new StudyPackDto.PracticeQuestion(entity.getId(), entity.getOrdinal(), entity.getType(),
                entity.getQuestionText(),
                entity.getOptions() == null ? List.of() : entity.getOptions(),
                entity.getDifficulty().name(),
                entity.getSourceRef());
    }

    private StudyPackageContent readContent(Map<String, Object> content) {
        if (content == null || content.isEmpty()) {
            return StudyPackageContent.empty();
        }
        try {
            StudyPackageContent parsed = objectMapper.convertValue(content, new TypeReference<>() {
            });
            if (parsed == null) {
                return StudyPackageContent.empty();
            }
            return new StudyPackageContent(
                    parsed.topics() == null ? List.of() : parsed.topics(),
                    parsed.revisionSections() == null ? List.of() : parsed.revisionSections(),
                    parsed.definitions() == null ? List.of() : parsed.definitions(),
                    parsed.formulas() == null ? List.of() : parsed.formulas());
        } catch (IllegalArgumentException exception) {
            log.warn("Stored study package content could not be read");
            return StudyPackageContent.empty();
        }
    }

    private String documentName(StudyPack pack, UUID ownerId) {
        try {
            DocumentStatusDto document = documentService.getStatus(pack.getDocumentId(), ownerId);
            return document.originalFilename();
        } catch (AppException exception) {
            return "Deleted document";
        }
    }

    private StudyPack requirePack(UUID studyPackId) {
        return studyPackRepository.findById(studyPackId)
                .orElseThrow(() -> new NotFoundException("That study package could not be found."));
    }

    private StudyPack requireOwnedPack(UUID studyPackId, UUID ownerId) {
        return studyPackRepository.findByIdAndOwnerId(studyPackId, ownerId)
                .orElseThrow(() -> new NotFoundException("That study package could not be found."));
    }

    private StudyPack requireReadyPack(UUID studyPackId, UUID ownerId) {
        StudyPack pack = requireOwnedPack(studyPackId, ownerId);
        if (pack.getStatus() != StudyPackStatus.READY) {
            throw new ValidationException("This study package is not ready yet.");
        }
        return pack;
    }

    private QuizAttempt requireOwnedAttempt(UUID attemptId, UUID ownerId) {
        return attemptRepository.findByIdAndOwnerId(attemptId, ownerId)
                .orElseThrow(() -> new NotFoundException("That examination could not be found."));
    }

    private StudyPreferences normalise(StudyPreferences preferences) {
        StudyPreferences defaults = StudyPreferences.defaults();
        if (preferences == null) {
            return defaults;
        }
        String language = preferences.language() == null || preferences.language().isBlank()
                ? defaults.language() : preferences.language().strip();
        String level = preferences.academicLevel() == null || preferences.academicLevel().isBlank()
                ? defaults.academicLevel() : preferences.academicLevel().strip();
        return new StudyPreferences(
                language.length() > 40 ? language.substring(0, 40) : language,
                level.length() > 60 ? level.substring(0, 60) : level,
                preferences.examDate(),
                clamp(preferences.questionCount(), 5, 30, defaults.questionCount()),
                clamp(preferences.flashcardCount(), 4, 40, defaults.flashcardCount()),
                clamp(preferences.examQuestionCount(), 4, 20, defaults.examQuestionCount()),
                clamp(preferences.examDurationMinutes(), 5, 120, defaults.examDurationMinutes()));
    }

    private int clamp(int value, int min, int max, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private int percentage(int score, int total) {
        return total <= 0 ? 0 : (int) Math.round(100.0 * score / total);
    }

    private String safeFailure(StudyPack pack) {
        return pack.getStatus() == StudyPackStatus.FAILED ? pack.getFailureReason() : null;
    }
}
