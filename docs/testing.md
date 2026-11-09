# Testing

## Running the suite

```bash
./mvnw verify          # compiles, runs all tests, packages the application
./mvnw test            # tests only
scripts/verify.sh      # same as verify, from any directory
```

Requirements: JDK 21. No Docker daemon, no external PostgreSQL and no Gemini API key are needed.

Integration tests use `io.zonky.test:embedded-postgres`, which downloads and starts a real
PostgreSQL binary on a random port for the test JVM. That means Flyway migrations, constraints,
JSONB columns and Hibernate schema validation are exercised against the real database engine rather
than an in-memory substitute. The binary is cached under the user's home directory after the first
run.

`src/test/resources/application-test.yml` disables Thymeleaf caching, keeps `ddl-auto=validate` and
sets an empty API key so no test can accidentally call Gemini.

## Inventory

| Test class | Tests | What it proves |
| --- | --- | --- |
| `ExamPrepApplicationTests` | 2 | Context starts; all three Flyway migrations apply and Hibernate validates the schema. |
| `AuthenticationIntegrationTest` | 8 | Registration rules (mismatch, weak password, duplicate email, BCrypt hashing), login success and failure, logout via POST, CSRF rejection, anonymous redirects, public pages render. |
| `DocumentParserTest` | 7 | Page-aware PDF extraction, DOCX heading sections, PPTX slide references, Markdown headings, Tika fallback for RTF, corrupted-PDF rejection, chunk bounding and unique reference ids. |
| `DocumentValidationServiceTest` | 6 | Real PDF accepted, path components stripped from file names, unsupported extension rejected, spoofed content type rejected, oversized file rejected, empty file rejected. |
| `DocumentWorkflowIntegrationTest` | 5 | Upload → async extraction → `READY` with page labels for PDF/DOCX/PPTX; status API shape; unsupported and spoofed uploads rejected with a flash error; cross-student access returns `404`; deletion removes the document and its chunks. |
| `StudyMaterialGenerationTest` | 4 | Valid JSON becomes a validated draft; invented citations are dropped; invalid JSON is retried once by the repair path and then fails; rate limits are retried by the gateway; a missing key fails with a configuration error before any call. |
| `StudyPackIntegrationTest` | 5 | Exam page contains no answer key; submission is scored server-side and readable on the results page; practice feedback returns the correct answer and explanation; short answers are not auto-graded; another student gets `404` for every study route. |
| `TutorIntegrationTest` | 2 | Tutor answer keeps only real citations, persists both messages and replays the conversation; blank questions are rejected without an AI call. |
| `AuthenticatedPageSmokeTest` | 2 | Every authenticated page and study section renders, including the results page after a real submission and the status page for a queued pack. |
| `ProcessingRecoveryTest` | 2 | Interrupted documents and packs are failed at startup with a readable reason; finished work is left untouched. |

Total: 43 tests. Tests never make paid AI calls: `StudyMaterialGenerationTest` uses a stubbed
transport, and `TutorIntegrationTest` replaces the gateway with a Mockito bean.

## What is covered indirectly

- CSRF on JSON endpoints is asserted by `AuthenticationIntegrationTest`.
- Journal of state transitions is covered by the workflow tests, which poll the persisted status
  rather than sleeping for a fixed time.
- Ownership isolation is asserted for pages, status API, exam submission, deletion and tutor routes.

## Not covered by automated tests

| Area | Why | How to verify manually |
| --- | --- | --- |
| Real Gemini responses | No API key and no paid calls | Set `GOOGLE_API_KEY`, upload a document, generate a pack, inspect citations |
| Docker image and Compose stack | Docker daemon unavailable while building this project | `docker compose up --build`, then follow the checklist below |
| Browser interactions | No browser in the build environment | Open the pages and exercise the JavaScript |
| Multi-instance recovery | Single-instance design | Documented limitation |

## Manual verification checklist

1. `cp .env.example .env`, set `POSTGRES_PASSWORD` and `GOOGLE_API_KEY`, then
   `docker compose up --build`.
2. Open <http://localhost:8080>. The landing page renders and `/login` and `/register` are reachable.
3. Register a student. You land on the login page with a confirmation message; sign in.
4. Upload a text-based PDF (at least a page of prose). The document page shows progress, then
   `READY` with page and section counts. A scanned PDF should end as `FAILED` with an explanation.
5. Generate a study package. The status page polls until `READY`, then shows the overview.
6. Visit topics, notes, definitions, formulas and flashcards. Flip a card and rate it.
7. Open practice, answer a multiple-choice question, confirm immediate feedback and explanation.
8. Start the mock exam. Confirm the page source contains no correct answer text. Submit, then check
   the score and per-question review on the results page.
9. Ask the tutor a question that the document answers and one it does not. Confirm citations point at
   real sections and that the second answer says the material is insufficient.
10. Register a second account and try the first account's document and study-pack URLs directly.
    Both must return `404`.
11. Delete the document. Its study pack and stored file must disappear from the library.
12. Stop the application while a package is generating, restart it, and confirm the pack shows
    `FAILED` rather than staying in progress.

## Housekeeping

- Generated uploads go to `target/test-uploads` during tests; `target/` is ignored by Git.
- Surefire reports are written to `target/surefire-reports`.
- The embedded PostgreSQL data directory lives under the user's home directory and is reused across
  runs.
