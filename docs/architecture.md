# ExamPrep architecture

## Shape of the system

ExamPrep is a modular monolith. A single Spring Boot 3.5 process serves the Thymeleaf interface, the JSON
endpoints used by browser interactions, background processing and all persistence. PostgreSQL is the only
external stateful dependency; the self-hosted FreeLLMAPI router is the only external service.

```
Browser (Thymeleaf pages + vanilla JS)
        │  session cookie, CSRF token
        ▼
web/ controllers ──────────────► api/ application services (ownership enforced here)
                                        │
                     ┌──────────────────┼───────────────────┐
                     ▼                  ▼                   ▼
                document/             study/               ai/
          validation, storage,   packages, cards,     FreeLLMAPI gateway, prompts,
          extraction, chunking   questions, exams,    structured generation,
                                 tutor, progress      retrieval, validation
                     │                  │                   │
                     └──────────── JPA / Flyway ────────────┘
                                        │
                                  PostgreSQL
```

Module boundaries are Java packages, not services. Controllers never touch repositories or the AI HTTP client;
they call the interfaces in `com.examprep.api`. The AI package knows about documents only through
`DocumentApplicationService`, which returns validated `DocumentContext` chunks.

## Processing pipeline

### 1. Upload

1. `DocumentValidator` checks extension, size and the **detected** content type (Tika magic detection),
   never the browser's filename or MIME type.
2. `FileSystemDocumentStorage` writes the file under a generated name in a private directory outside
   `static/`.
3. A `documents` row is created with status `UPLOADED`, then an async extraction job is scheduled.
4. The browser receives the document ID immediately and polls `GET /api/documents/{id}/status`.

### 2. Extraction

`DocumentProcessingService` runs on a bounded executor:

- PDF → PDFBox, page by page, labels `Page N`.
- DOCX/DOC/PPTX/PPT → POI, labels `Section: <heading>` or `Slide N`.
- TXT/MD/RTF/ODT/ODP → Tika, label `Document text`.
- `TextChunker` normalises whitespace and splits text into chunks of at most ~1,900 characters, never
  crossing a structural boundary. Each chunk gets a stable reference `c1`, `c2`, … and its real location
  label.
- Fewer than 200 useful characters marks the document `FAILED` with a scanned-PDF explanation.
- Any failure stores a safe, human-readable reason; document text is never logged.

### 3. Study generation

`StudyPackApplicationService.create` validates ownership and readiness, saves a `QUEUED` package and
schedules `StudyGenerationService`:

1. Load `DocumentContext` (all chunks with real labels).
2. If the document exceeds the source budget, excerpts are sampled evenly across the whole document, and
   the document is split into segments that are analysed separately with the AI router; the analyses are
   then combined into a brief. This avoids one enormous request while keeping back-half coverage.
3. Send one structured JSON request (system instruction + preferences + chunks + section analyses).
4. Parse with Jackson; on malformed output, request one repair pass, then fail with a stored explanation.
5. `StudyPackageSanitizer` validates every field:
   - questions must have the right structure for their type and a valid correct answer;
   - duplicates and blank content are dropped;
   - every citation is mapped from chunk reference IDs to real location labels; unknown references are
     discarded.
6. Persist flashcards and questions relationally; keep topics/notes/definitions/formulas in JSONB; mark the
   package `READY`.

Progress (`QUEUED → PROCESSING → READY/FAILED`, message and percentage) is stored in PostgreSQL, so the
browser can poll and a restart never leaves a permanently spinning job: stale jobs are failed by
`DocumentStaleJobRecovery` / `StudyStaleJobRecovery` and can be retried.

### 4. Mock examinations

`startExam` selects exam-eligible questions (multiple choice and true/false) at random, creates a
`quiz_attempts` row plus ordered `quiz_attempt_answers` placeholders and returns an `ExamSessionDto` that
contains **questions and options only**.

`submitExam` loads the attempt by owner, records each answer, marks it correct against stored answers,
updates the score and time, and returns the result. Repeated submissions return the stored result. The
result DTO is the only place correct answers, explanations and revision areas appear.

### 5. Tutor

`TutorService` retrieves the chunks with the highest lexical overlap with the question (stop words removed,
character budget enforced), builds a prompt containing only those chunks and the last ten conversation
messages, and asks for JSON containing `answer`, `answeredFromMaterial` and `sources`. Sources are validated
against the retrieved chunks before being displayed. When the model reports insufficient material, the UI is
told explicitly and no sources are shown.

## Data model

| Table | Purpose |
| --- | --- |
| `users` | Student accounts; unique lower-case email index, BCrypt hash |
| `documents` | Upload metadata, storage key, processing status, failure reason |
| `document_chunks` | Stable `ref_id`, ordinal, real location label, chunk text |
| `study_packs` | Generation state/progress, preferences, overview fields, JSONB sections |
| `flashcards` | Relational cards for review |
| `questions` | Type, options (JSONB), correct answer, explanation, difficulty, source |
| `quiz_attempts` | Exam attempt, score, duration, timing, submission |
| `quiz_attempt_answers` | Per-question answer and correctness |
| `tutor_messages` | Conversation history with JSONB source labels |
| `flashcard_reviews` | Completed review sessions for the progress overview |

Flyway owns the schema (`V1`–`V3`); Hibernate runs with `ddl-auto: validate`. UUID primary keys and foreign
keys with `ON DELETE CASCADE` give clean ownership deletion.

## Transactions and ownership

- Read paths check `owner_id` in the repository query itself (`findByIdAndOwnerId`), so a foreign UUID is
  indistinguishable from a missing one.
- Exam attempts are loaded by `id + owner`, and the attempt's package ID must match the requested package.
- Long AI calls are never wrapped in a database transaction; state is written in small steps so progress is
  visible and no connection is held during an HTTP request to the AI router.
- Processing updates are idempotent (delete-then-insert for generated resources).

## Security

- Session-based Spring Security, BCrypt, CSRF enabled for all forms and JSON mutations, same-site cookie,
  session fixation migration.
- Filters protect every route except the landing, auth, error and static assets.
- Uploads: extension allowlist + magic-byte detection, size limits, generated storage names, path traversal
  guard, private directory.
- Prompts explicitly treat document text as untrusted study material; the system instruction is the only
  source of AI policy. Output is validated before persistence and rendering.
- The FreeLLMAPI proxy key is read from the environment, held by the shared HTTP client and never logged
  or exposed.
- Errors shown to students are app-authored messages; stack traces stay in server logs.

## Deliberate simplifications

- Lexical chunk retrieval instead of a vector store (per the brief).
- JSONB for the read-only study narrative; relational rows where scoring and ownership matter.
- Single-process background executors rather than a broker or queue.
- Session authentication rather than JWTs, since the UI is server-rendered.
