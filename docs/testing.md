# Testing ExamPrep

## Running the suite

```bash
./mvnw clean verify
```

The tests start a real PostgreSQL instance from embedded binaries (Zonky) and run Flyway plus Hibernate
schema validation against it. No local database is needed, and **no Gemini API key is required** — AI calls
are replaced by a mocked `GeminiGateway`.

Docker Compose startup is a separate manual check (see the README); automated tests do not require Docker.

## What is covered

### Unit tests
| Test | Covers |
| --- | --- |
| `TextChunkerTest` | stable chunk references, truthful page/slide labels, boundary-respecting splits, empty content, whitespace normalisation |
| `DocumentValidatorTest` | supported text upload, unsupported extension, content/extension mismatch, empty file, oversized file, path stripping |
| `StudyPackageSanitizerTest` | fabricated citation removal, malformed question rejection, valid question preservation, empty-package rejection, title fallback |
| `SourceReferencesTest` | resolving real references, discarding invented IDs/page numbers, deduplication |
| `ChunkRetrieverTest` | relevance ranking, fallback chunk, character budget |

### Integration tests (embedded PostgreSQL, MockMvc, real Thymeleaf rendering)
| Test | Covers |
| --- | --- |
| `AuthFlowIntegrationTest` | public landing/auth pages, protected redirects, registration + BCrypt hash + auto sign-in, duplicate email, password mismatch, valid/invalid login, logout invalidates session |
| `DocumentWorkflowIntegrationTest` | TXT upload → async extraction → `READY`, chunk references and labels, ownership isolation (service and HTTP), document pages render, unsupported upload rejected, image-only text fails with a clear message, deletion |
| `StudyWorkflowIntegrationTest` | mocked structured Gemini response → package persisted (topics, notes, definitions, formulas, flashcards, questions), every study page renders, exam start excludes answers from HTML, submission scores 3/4 deterministically, results page, resubmission/idempotency, practice reveal, tutor answer with validated sources and persisted history, flashcard review endpoint |
| `AiConfigurationIntegrationTest` | missing `GOOGLE_API_KEY` produces an actionable error, generation stores `FAILED` with the configuration explanation and does not fabricate content, tutor refuses a non-ready package |

### AI failure handling

The gateway maps provider failures to safe messages and retries transient conditions (429, 5xx, timeouts)
with exponential backoff. Malformed JSON triggers exactly one repair request, then a stored `FAILED` status.
Automated tests never make paid API calls.

## Not covered automatically

- **Live Gemini behaviour** (model availability, real latency, quota). This requires the owner's API key;
  see the manual smoke test below.
- **Docker image build and Compose startup** locally (the GitHub Actions workflow builds the image after
  tests pass).
- **Browser interaction details** such as drag-and-drop, the exam countdown and flashcard keyboard
  shortcuts. JavaScript is included and reviewed, but no browser automation suite is part of this release.
- **Tablet/mobile visual review** — the layout uses responsive breakpoints at 960px and 720px; a manual
  visual pass is recommended before a public launch.

## Manual Gemini smoke test

With `GOOGLE_API_KEY` set and a real document:

1. Upload a text-based PDF or DOCX; confirm it becomes `READY` with page/slide labels.
2. Generate a study package; confirm progress advances and the package becomes `READY`.
3. Check that topics and definitions cite real locations and that no invented pages appear.
4. Start a mock exam; confirm no answers are visible in page source before submission.
5. Submit and confirm the score, explanations and revision areas.
6. Ask the tutor a question answered by the document, then one it cannot answer; confirm the second says
   the material is insufficient.
7. Unset the key and confirm generation and the tutor show a configuration error rather than fake content.
