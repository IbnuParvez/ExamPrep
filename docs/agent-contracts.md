# Agent contracts

ExamPrep is organised around five functional agents. They are not separate services; each is a
module boundary with an explicit contract. This document states what each agent accepts, what it
guarantees and how it fails, so behaviour can be changed in one place without breaking the others.

The central rule for every agent: **document content is data, never instructions.** Text extracted
from an upload may contain attempts to redirect the model. Prompts treat it as untrusted, and the
server validates every structured response before it is stored or shown.

---

## 1. Intake agent — `com.examprep.document`

**Purpose:** turn a browser upload into a stored, owned, validated document.

**Input**

- `MultipartFile` from an authenticated request.
- `ownerId` resolved from the session by `CurrentUserService`.

**Output**

- `DocumentUploadResult(documentId, originalFilename, status)`.
- A `documents` row with status `UPLOADED`, a generated storage key and the sniffed content type.

**Guarantees**

- The extension is on the allow list: `pdf, docx, doc, pptx, ppt, txt, md, rtf, odt, odp`.
- `0 < size <= app.upload.max-size-mb`.
- The Tika-detected content type belongs to the declared format's family, so renaming a JPEG to
  `.pdf` is rejected.
- The file name is reduced to its last path component and control characters are removed; it is
  never used as a filesystem path.
- The stored path resolves inside the configured storage root; traversal attempts are rejected.
- Storage is outside every static resource directory.

**Failure**

| Condition | Result |
| --- | --- |
| Empty file | `ValidationException("Please choose a file to upload.")` |
| Unsupported extension | `ValidationException` naming the supported formats |
| Content mismatch | `ValidationException("The file content does not look like …")` |
| Too large | `ValidationException` with the limit; Spring's multipart limit also applies |
| Storage I/O error | `StorageException`; no row is created |

---

## 2. Extraction agent — `com.examprep.document.parser`, `DocumentExtractionWorker`

**Purpose:** convert a stored file into ordered, citable sections.

**Input:** document id; the stored file and its format.

**Output:** `ExtractedDocument(pageCount?, sections)` and, after chunking, `DocumentChunk` rows with
`S1…Sn`, a location label and the chunk text. Status becomes `READY` with page and character counts.

**Guarantees**

- Runs asynchronously; the upload request never waits for extraction.
- The parser is chosen by format with a deterministic order: PDF → PPTX → DOCX → plain text → Tika.
- Page and slide numbers come from the document structure, not from guessing: `Page N`, `Slide N`,
  `Section: <heading>`, or `Document` when the format has no reliable boundaries.
- Text is normalised (line endings, control characters, redundant whitespace) before chunking so
  reference ids stay stable across runs.
- Chunks are bounded by `max-chunk-characters` and prefer paragraph and sentence boundaries.
- Text shorter than `minimum-meaningful-characters` is rejected rather than producing an empty pack.
- Text longer than `max-extracted-characters` is rejected with a request to split the document.

**Failure**

| Condition | Result |
| --- | --- |
| Corrupt PDF or archive | `FAILED`, "may be corrupted or damaged" |
| Password-protected PDF | `FAILED`, asks the student to remove protection |
| Scanned PDF (no text layer) | `FAILED`, explains that OCR is not supported |
| Too little or too much text | `FAILED`, specific message |
| Unexpected error | `FAILED`, generic message; details logged without content |

Re-submitting a job for a terminal document is a no-op, so duplicate scheduling cannot corrupt state.

---

## 3. Study designer agent — `com.examprep.ai`, `com.examprep.study`

**Purpose:** produce the study package from extracted sections.

**Input**

- The document's sections (`S1…Sn` with text and location labels).
- `StudyPreferences`: language, academic level, optional exam date, question count, flashcard count,
  exam duration.
- `AppProperties.Study` limits.

**Output:** a validated `StudyMaterialDraft` containing title, introduction, summary, learning
objectives, revision priorities, topics, revision sections, definitions, formulas, key facts,
flashcards, questions and an exam duration. Persisted as JSONB and mapped to `StudyPackDto`.

**Guarantees**

- The request contract is JSON with a fixed shape; `responseMimeType=application/json` is requested
  and the response is parsed into typed records, not trusted as text.
- One repair attempt is made if parsing or validation fails; the second failure ends the job.
- Every citation is normalised against the chunk ids that were actually sent. Unknown ids are
  dropped, so a hallucinated `S99` can never become a link.
- Requested counts are clamped to `max-question-count` and `max-flashcard-count`.
- Documents above the single-pass ceiling use map/reduce; the final response must still satisfy the
  same contract.
- Answers and explanations are stored server-side only. They are not part of the DTO served to the
  exam page.

**Failure**

| Condition | Result |
| --- | --- |
| Missing API key | `AiConfigurationException`; pack `FAILED`; no network call |
| Rate limit / 5xx | Retried with exponential backoff, then `FAILED` |
| Invalid JSON twice | `AiGenerationException`; pack `FAILED` |
| Timeout | `AiUnavailableException`; pack `FAILED` |
| Task queue full | Rejected task recorded as a failure |

---

## 4. Examiner agent — `com.examprep.study` (questions, attempts, scoring)

**Purpose:** run practice and the timed mock examination without leaking the answer key.

**Input**

- Pack id and `ownerId`.
- `QuizSubmissionDto(answers[{questionId, answer}], timeTakenSeconds)` for an exam.
- `{answer}` for a single practice question.

**Output**

- Exam page model: prompts, options, difficulty, source reference, duration. No answers.
- `QuizResultDto(attemptId, score, totalQuestions, percentage, items[…], revisionAreas)` after
  submission, with correct answers and explanations included.
- `PracticeAnswerDto(questionId, autoGraded, correct, correctAnswer, explanation, sourceReference)`.

**Guarantees**

- Only `MULTIPLE_CHOICE` and `TRUE_FALSE` questions are `exam_eligible`. Short answers are
  practice-only, because free text cannot be scored deterministically.
- Scoring uses the stored key, never anything supplied by the client.
- The submission is persisted before the result is returned, so the results page can be reopened.
- Missing answers count as incorrect rather than aborting the attempt.
- Ownership is checked before any question is read or written.
- An unknown `questionId` in a submission is ignored; it cannot influence the score.

---

## 5. Tutor agent — `com.examprep.ai` (tutor), `com.examprep.study` (conversation)

**Purpose:** answer a follow-up question from the document, with citations.

**Input:** pack id, `ownerId`, `{question}` (trimmed, non-blank, length-capped).

**Output:** `TutorAnswerDto(answer, sourceReferences[], insufficientInformation)`. Question and answer
are stored as `tutor_messages` and replayed on the tutor page.

**Guarantees**

- Context is limited to `app.tutor.max-context-chunks` sections selected by IDF-weighted keyword
  overlap. The same selection is deterministic for the same question and document.
- The prompt contains only those sections and instructs the model to say when the material does not
  cover the question instead of inventing an answer.
- Returned references are intersected with the ids that were sent; unknown ids are dropped.
- A blank question is rejected with `400` before any AI call.
- If no API key is configured, the endpoint fails clearly instead of producing a fabricated answer.

---

## Cross-agent invariants

1. **Ownership.** Every read and write is scoped by the authenticated user id in the service layer.
   Foreign ids produce `404`.
2. **Status transitions.** Terminal states are immutable; starting work checks `isTerminal()` first.
3. **No answer leakage.** The exam page DTO contains no correct answer or explanation. The results
   page is reachable only after a persisted attempt.
4. **Citations are evidence.** A citation is accepted only if it points at a section that was
   supplied to the model and exists in the database.
5. **Secrets stay server-side.** The API key is read from the environment, redacted in
   `toString()`, and never placed in a template model or log.
6. **Content is data.** Document text is fenced as source material in every prompt, and the model is
   told to ignore instructions inside it.
7. **Readable failure.** Students see actionable messages; stack traces and document content stay in
   logs, and logs never include credentials or full document text.
