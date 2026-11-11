# Shared contracts and module ownership

This document is the contract record for the five workstreams in the ExamPrep development brief. The
interfaces and DTOs below are the only way modules talk to each other; all live in `com.examprep.api`.

## Application service contracts

```java
public interface CurrentUserService {
    UUID requireCurrentUserId();
}

public interface UserRegistrationService {
    UserAccountDto register(RegisterUserRequest request);
}

public interface DocumentApplicationService {
    DocumentUploadResult upload(MultipartFile file, UUID ownerId);
    DocumentStatusDto getStatus(UUID documentId, UUID ownerId);
    List<DocumentSummaryDto> listDocuments(UUID ownerId);
    DocumentContext getContext(UUID documentId, UUID ownerId);
    void retryExtraction(UUID documentId, UUID ownerId);
    void delete(UUID documentId, UUID ownerId);
}

public interface StudyPackApplicationService {
    StudyPackCreationResult create(UUID documentId, UUID ownerId, StudyPreferences preferences);
    StudyPackStatusDto getStatus(UUID studyPackId, UUID ownerId);
    StudyPackDto getStudyPack(UUID studyPackId, UUID ownerId);
    List<StudyPackSummaryDto> listStudyPacks(UUID ownerId);
    void delete(UUID studyPackId, UUID ownerId);
    StudyPackStatusDto retryGeneration(UUID studyPackId, UUID ownerId);
    ExamSessionDto startExam(UUID studyPackId, UUID ownerId);
    ExamSessionDto getExamSession(UUID attemptId, UUID ownerId);
    QuizResultDto submitExam(UUID studyPackId, UUID ownerId, QuizSubmissionDto submission);
    QuizResultDto submitExamAttempt(UUID attemptId, UUID ownerId, QuizSubmissionDto submission);
    QuizResultDto getResult(UUID attemptId, UUID ownerId);
    List<QuizAttemptSummaryDto> recentAttempts(UUID ownerId, int limit);
    PracticeAnswerDto revealPracticeAnswer(UUID studyPackId, UUID questionId, UUID ownerId);
    void recordFlashcardReview(UUID studyPackId, UUID ownerId, int reviewedCards, int totalCards);
    TutorAnswerDto askTutor(UUID studyPackId, UUID ownerId, TutorQuestionDto question);
    List<TutorMessageDto> tutorHistory(UUID studyPackId, UUID ownerId);
}
```

`StudyPackApplicationService.submitExam` is the contract's `submitQuiz`; the attempt-based signature keeps
ownership checks explicit and supports idempotent resubmission.

## DTOs (`com.examprep.api.dto`)

| DTO | Fields |
| --- | --- |
| `RegisterUserRequest` | `fullName`, `email`, `password` (validated) |
| `UserAccountDto` | `id`, `fullName`, `email` |
| `DocumentUploadResult` | `documentId`, `originalFilename`, `status` |
| `DocumentStatusDto` | `id`, `originalFilename`, `status`, `errorMessage`, `chunkCount`, `pageCount`, `createdAt`; helpers `statusLabel()`, `ready()`, `failed()`, `inProgress()` |
| `DocumentSummaryDto` | `id`, `originalFilename`, `contentType`, `fileSizeBytes`, `status`, `createdAt`, `chunkCount`; helper `sizeLabel()` |
| `DocumentChunk` | `refId`, `ordinal`, `locationLabel`, `text` |
| `DocumentContext` | `documentId`, `originalFilename`, `title`, `chunks`; helper `totalChars()` |
| `StudyPreferences` | `language`, `academicLevel`, `examDate`, `questionCount`, `flashcardCount`, `examQuestionCount`, `examDurationMinutes`; `defaults()` |
| `StudyPackCreationResult` | `studyPackId`, `status` |
| `StudyPackStatusDto` | `id`, `status`, `progressMessage`, `errorMessage`, `progressPercent`; helpers `ready()`, `failed()`, `inProgress()` |
| `StudyPackDto` | package metadata + `learningObjectives`, `keyAreasToRevise`, `topics`, `revisionSections`, `definitions`, `formulas`, `flashcards`, `practiceQuestions`, `examQuestionCount`, `examDurationMinutes`, `status`; nested `Topic`, `RevisionSection`, `Definition`, `Formula`, `Flashcard`, `PracticeQuestion` |
| `StudyPackSummaryDto` | `id`, `documentId`, `title`, `documentFilename`, `status`, `flashcardCount`, `questionCount`, `createdAt` |
| `QuizAttemptSummaryDto` | `attemptId`, `studyPackId`, `studyPackTitle`, `score`, `totalQuestions`, `percentage`, `timeTakenSeconds`, `submittedAt` |
| `PracticeAnswerDto` | `questionId`, `type`, `correctAnswer`, `explanation`, `sourceRef` |
| `ExamSessionDto` | `attemptId`, `studyPackId`, `studyPackTitle`, `durationSeconds`, `timeTakenSeconds`, `startedAt`, `questions` (nested `Question`: `questionId`, `position`, `type`, `question`, `options`) — **no answers** |
| `QuizSubmissionDto` | `attemptId`, `timeTakenSeconds`, `confirmed`, `answers` (nested `Answer`: `questionId`, `selectedOptionIndex`, `booleanAnswer`) |
| `QuizResultDto` | `attemptId`, `studyPackId`, `studyPackTitle`, `score`, `totalQuestions`, `percentage`, `timeTakenSeconds`, `submittedAt`, `items`, `revisionAreas` (nested `Item` with student answer, correct answer, explanation, source) |
| `TutorQuestionDto` | `message` (validated, max 2000) |
| `TutorAnswerDto` | `answer`, `sources`, `answeredFromMaterial`, `answeredAt` |
| `TutorMessageDto` | `role`, `content`, `sources`, `createdAt` |

## Enums (`com.examprep.api.model`)

- `DocumentStatus`: `UPLOADED`, `PROCESSING`, `READY`, `FAILED` (+ `isTerminal()`, `label()`).
- `StudyPackStatus`: `QUEUED`, `PROCESSING`, `READY`, `FAILED` (+ `isTerminal()`, `label()`).
- `QuestionType`: `MULTIPLE_CHOICE`, `TRUE_FALSE`, `SHORT_ANSWER` (+ `label()`, `isAutoScored()`).
- `Difficulty`: `EASY`, `MEDIUM`, `HARD`.

Internal AI DTOs (`com.examprep.ai.dto`) mirror the AI JSON contract and never leave the AI module
except as sanitised `StudyPackDto` content.

## Ownership boundaries

| Workstream | Owner packages and files |
| --- | --- |
| 1. Platform foundation and integration | `pom.xml`, `mvnw*`, `ExamPrepApplication`, `config/**`, `security/**`, `user/**`, `common/**`, `api/**`, `application.yml`, `V1__users.sql`, architecture/contract docs |
| 2. Document ingestion | `document/**`, `V2__documents.sql`, upload/extraction tests |
| 3. AI router and study engine | `ai/**`, `study/**`, `V3__study_and_ai.sql`, AI/quiz tests |
| 4. Browser interface and UX | `web/**`, `templates/**`, `static/**` |
| 5. QA, Docker and release | `src/test/**`, `.github/workflows/**`, `Dockerfile`, `compose.yaml`, `README.md`, `docs/testing.md`, `.env.example` |

Contracts may only change with the integration owner's agreement. In this delivery one agent executed all
five workstreams, so the branches and pull requests described in the brief were replaced by one integrated
change set on the default branch; the ownership table above was respected while editing, and migrations were
numbered V1→V3 in dependency order.

## Cross-module invariants

1. A document must be `READY` before a study package can be created or a context requested.
2. `DocumentContext` chunks always carry a real `locationLabel` produced by an extractor or the generic
   `Document text` label; no page numbers are invented.
3. AI citations use `refId` values only; `SourceReferences` maps them to labels and drops unknown IDs.
4. `ExamSessionDto` never contains answers, explanations or correctness; only `QuizResultDto` does, after
   submission.
5. Every service method that takes a `ownerId` verifies it; controllers obtain it from
   `CurrentUserService`, never from request parameters.
