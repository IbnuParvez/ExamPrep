# Architecture

## Shape of the system

ExamPrep is a modular monolith: one Spring Boot process, one PostgreSQL database, one private file
store. Modules communicate through interfaces and DTOs in `com.examprep.api`, never by reaching into
each other's repositories. That keeps a later extraction into services possible without rewriting
call sites, while avoiding the operational cost of microservices today.

```
Browser ──HTTP──► web (Thymeleaf pages, JSON API)
                     │
                     ▼
             api (interfaces + DTOs)
        ┌────────────┼───────────────┐
        ▼            ▼               ▼
    document       study             ai
   (uploads,   (packs, exams,   (gateway, prompts,
   parsers,     tutor, progress)  JSON validation)
   chunking)
        │            │               │
        └────────────┴───────┬───────┘
                             ▼
                     PostgreSQL + file store
```

### Modules

| Module | Responsibility |
| --- | --- |
| `web` | Page controllers, JSON API, exception handling, view formatting. No business rules. |
| `document` | Upload validation, private storage, format-specific extraction, chunking, async worker, document status. |
| `study` | Study pack lifecycle, generated content, questions, attempts, flashcards, tutor conversation, progress. |
| `ai` | Gemini transport and gateway, prompt construction, JSON parsing and repair, citation resolution, retrieval. |
| `security` | Filter chain, CSRF, session login, principal, current-user resolution. |
| `user` | Account entity, repositories, registration rules. |
| `config` | Typed `app.*` and `ai.gemini.*` properties, async executors. |

## Data model

Flyway owns the schema; Hibernate only validates it.

| Table | Purpose | Notable constraints |
| --- | --- | --- |
| `users` | Accounts | unique, case-insensitive email; BCrypt hash; `created_at` |
| `documents` | Uploaded files | owner FK, status, format, storage key, counts, failure reason |
| `document_chunks` | Extracted sections | `(document_id, chunk_index)` unique, `reference_id` (`S1…Sn`), location label |
| `study_packs` | Generated packages | owner FK, document FK, status, preferences, JSONB content columns |
| `questions` | Generated questions | type, options JSONB, correct answer, explanation, source reference, `exam_eligible` |
| `exam_attempts` | Submitted exams | score, totals, duration, answers JSONB, review data |
| `flashcards` | Generated cards | front, back, source reference |
| `flashcard_reviews` | Self-assessment | per student, card, rating |
| `tutor_messages` | Tutor conversation | role, content, validated citations JSONB |

Cascade rules are deliberate: deleting a document cascades to chunks and study packs; deleting a
study pack cascades to questions, flashcards, attempts and tutor messages. Deleting a student is not
exposed by the application.

Statuses are explicit rather than inferred:

- Document: `UPLOADED → PROCESSING → READY | FAILED`
- Study pack: `QUEUED → PROCESSING → READY | FAILED`

Both use a `isTerminal()` check before starting work, which makes duplicate task submission a no-op.

## Request paths

### Upload and extraction

1. `POST /documents` validates the multipart file (`DocumentValidationService`): non-empty, extension
   on the allow list, size within `app.upload.max-size-mb`, and Tika-detected content type belonging to
   the same family as the extension. A JPEG renamed to `.pdf` is rejected here.
2. `DocumentStorageService` streams the file to `<root>/<ownerId>/<random>.<ext>`, never using the
   client file name as a path, and refuses keys that escape the root.
3. A `documents` row is inserted as `UPLOADED` and the response redirects immediately.
4. `DocumentExtractionWorker.process` runs on the `documentTaskExecutor` pool: mark `PROCESSING`,
   resolve the stored path, pick the extractor for the format, normalise text, enforce the minimum and
   maximum character bounds, chunk, and write chunks plus `READY` in one transaction
   (`DocumentStateWriter`).
5. Failures are recorded as `FAILED` with a message intended for the student; technical detail is
   logged without document content.

Parsers:

| Format | Parser | Structure used for citations |
| --- | --- | --- |
| PDF | PDFBox | one section per page → `Page N` |
| DOCX | POI via Tika dependency | headings → `Section: <heading>` |
| PPTX | POI via Tika dependency | slides → `Slide N` |
| TXT / MD | plain reader (UTF-8 with ISO-8859-1 fallback) | Markdown headings → `Section: <heading>` |
| DOC, PPT, RTF, ODT, ODP | Tika auto-detect | whole document → `Document` |

Chunking keeps sections intact where possible, splits on paragraph and sentence boundaries when a
section exceeds `max-chunk-characters`, and assigns `S1…Sn` in reading order. The reference id and
human-readable location label travel together, so a citation is both stable and displayable.

### Study pack generation

`POST /documents/{id}/study-packs` records a `study_packs` row as `QUEUED` with the student's
preferences and schedules `StudyPackGenerationWorker` on the `aiTaskExecutor` pool.

The worker:

1. Marks the pack `PROCESSING`.
2. Loads the document's chunks through the document module.
3. Calls `StudyMaterialGenerator`, which decides between two strategies:
   - **Single pass** when the source text is at most `app.study.max-source-characters-per-request`
     (24 000 characters by default). One request, one JSON object.
   - **Map/reduce** above that. Each batch is summarised into a compact JSON summary; the summaries
     are reduced into the final package, still under the same JSON contract.
4. Validates the response into typed records, normalises citations against the supplied chunk ids,
   and derives `exam_eligible` flags.
5. Persists content, flashcards and questions in one transaction and marks the pack `READY`.

### Exam and practice

`GET /study/{id}/exam` returns question prompts, options and metadata but never the correct answer or
explanation. `POST /api/study/{id}/exam` accepts `questionId`/`answer` pairs, scores them against the
stored key, persists an attempt with the reviewed answers, and only then exposes correct answers and
explanations. Only `MULTIPLE_CHOICE` and `TRUE_FALSE` questions are exam eligible; short-answer
questions are practice-only because they cannot be scored deterministically.

Practice feedback (`POST /api/study/{id}/practice/{questionId}`) returns the correct answer and
explanation immediately, and marks short answers as `autoGraded=false` while showing the model answer.

### Tutor

`POST /api/study/{id}/tutor` selects up to `app.tutor.max-context-chunks` sections using IDF-weighted
keyword overlap between the question and the chunk text, builds a grounded prompt, and asks for JSON
containing an answer, source references and an `insufficientInformation` flag. References are filtered
against the chunk ids that were actually supplied. Both the question and the validated answer are
persisted, and the conversation is replayed when the page is reopened.

## AI boundary

`GeminiTransport` is the narrow port to the network; `SdkGeminiTransport` implements it with
`com.google.genai`. `GoogleGeminiGateway` adds timeout configuration, bounded retries with exponential
backoff for rate limits and server errors, and maps failures to domain exceptions
(`AiRateLimitException`, `AiUnavailableException`, `AiGenerationException`,
`AiConfigurationException`).

`GeminiJsonClient` is responsible for the JSON contract:

- requests `application/json` with a low temperature;
- parses into the expected record shape and validates required fields;
- on a parse or validation failure, retries once with a repair instruction that includes the failure
  reason;
- if the second attempt fails, throws `AiGenerationException`; the worker marks the pack `FAILED`.

Prompts are defined in `PromptLibrary` and treat document text as data, not instructions. The system
instruction tells the model to ignore directives found inside the source, to use only the supplied
sections, and to return the agreed JSON shape.

## Security model

- Form login at `/login` with `email` and `password`, BCrypt hashing, session fixation protection.
- `SecurityConfig` permits `/`, `/login`, `/register`, static assets and error pages; everything else
  requires authentication. `X-Content-Type-Options`, frame denial and a same-origin referrer policy are
  set explicitly.
- CSRF uses `HttpSessionCsrfTokenRepository`. Page templates expose the token and header name as meta
  tags; JavaScript sends `X-CSRF-TOKEN` on every mutating request.
- `CurrentUserService.requireCurrentUserId()` is the only way services learn who is calling. Every
  repository lookup for a document or pack includes the owner id, and a miss throws
  `ResourceNotFoundException`, which renders `404` for both "does not exist" and "belongs to someone
  else".
- Upload validation happens before storage; storage prevents traversal; the static resource handler
  never serves the storage root.
- `GeminiProperties.toString()` redacts the key, and no key is placed in a model attribute or log line.

## Failure and recovery

| Situation | Behaviour |
| --- | --- |
| Corrupt or scanned document | Document `FAILED` with a readable reason; no chunks stored |
| Text below the minimum or above the maximum | Document `FAILED` with a specific message |
| Gemini rate limit or 5xx | Retried with backoff, then `FAILED` on the pack |
| Invalid JSON twice | `FAILED`; the student can regenerate |
| Missing API key | Configuration error surfaced, pack `FAILED`, no network call |
| Process restart mid-job | Startup recovery marks open documents and packs `FAILED`; the student retries |
| Session expiry | Next request redirects to login; polling JavaScript stops on `401` |

Recovery is intentionally simple: a single instance is assumed, so any job still open at startup
cannot be running and is safe to fail. A multi-instance deployment would need job leases instead.

## Configuration surface

`AppProperties` binds `app.*` (storage, upload, processing, study, tutor) and `GeminiProperties` binds
`ai.gemini.*`. Both are records with constructor validation where it matters, so invalid values fail
at startup rather than in the middle of a request. Executors are defined in `AsyncConfig` with bounded
queues; a rejected task is recorded as a failure instead of disappearing.

## Extension points

- Add a document format: extend `DocumentFormat` and add a `DocumentTextExtractor` with an `@Order`.
- Change the study shape: adjust the DTO records and `PromptLibrary`, then the mapper in the study
  module.
- Swap the AI provider: implement `GeminiTransport`; nothing above the gateway changes.
- Replace lexical retrieval: implement a different selector behind the tutor retrieval interface.
