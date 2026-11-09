# ExamPrep

ExamPrep is a browser-based examination preparation platform. A student uploads their own course
material, and the application turns it into a structured study package: an overview, topics,
revision notes, definitions, formulas, flashcards, practice questions, a timed mock examination and
a document-grounded tutor. Every question and answer is traceable to a real section of the uploaded
document, and everything is private to the student who uploaded it.

Built with Java 21, Spring Boot 3.5 and PostgreSQL, rendered with Thymeleaf and plain CSS and
JavaScript. No Node, no SPA framework, no vector database.

## What it does

- **Private document library.** Upload PDF, DOCX, DOC, PPTX, PPT, TXT, Markdown, RTF, ODT or ODP
  files up to a configurable size limit (25 MB by default). Files are validated by extension and by
  real content type, stored outside the web root with generated names, and extracted in the
  background so the browser never waits for a large file.
- **Grounded study material.** Gemini produces a JSON study package from the extracted text. The
  server validates the JSON, retries once with a repair prompt, and rejects any citation that does
  not point at a section that was actually supplied.
- **Study workspace.** Overview, topic explanations, revision notes, definitions, formulas,
  flashcards with self-assessment, practice questions with instant feedback, and a timed mock exam.
- **Server-side assessment.** Answers are stored, scored and explained on the server. The answer key
  never reaches the browser before the exam is submitted; only auto-scorable multiple-choice and
  true/false questions are used in the timed exam.
- **Document-grounded tutor.** Ask follow-up questions; the answer is drawn from the most relevant
  document sections and keeps a validated citation list.
- **Progress tracking.** Document, study-pack, flashcard, practice and exam activity feed a progress
  view on the dashboard.
- **Delete anything.** Deleting a document removes its stored file, extracted sections and every
  study package generated from it.

## Stack

| Layer | Choice |
| --- | --- |
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.5.16 (Web, Security, Data JPA, Validation) |
| Views | Thymeleaf, hand-written CSS, vanilla JavaScript |
| Database | PostgreSQL with Flyway migrations; Hibernate validates the schema at startup |
| Documents | Apache Tika, PDFBox (page-aware PDF), Apache POI via Tika (DOCX, PPTX) |
| AI | `com.google.genai:google-genai` 1.80.0, default model `gemini-3.8-flash` |
| Tests | JUnit 5, Mockito, Spring Boot Test, `io.zonky.test:embedded-postgres` |
| Delivery | Multi-stage Dockerfile running as a non-root user, Docker Compose |

## Quick start with Docker Compose

```bash
cp .env.example .env
# Edit .env: set POSTGRES_PASSWORD and GOOGLE_API_KEY
docker compose up --build
```

Open <http://localhost:8080>, register an account, upload a text-based document and generate a study
package. The first build downloads Maven dependencies and takes a few minutes.

The application refuses to start without `POSTGRES_PASSWORD`. It starts without `GOOGLE_API_KEY`,
but document uploads still work and generation or tutor requests return a clear configuration error
instead of calling the API.

## Local development

You need JDK 21 and a PostgreSQL 14+ database (or Docker for just the database).

```bash
# Database only
docker compose up -d postgres

export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/examprep
export SPRING_DATASOURCE_USERNAME=examprep
export SPRING_DATASOURCE_PASSWORD=change-me-before-production
export GOOGLE_API_KEY=your-key
export APP_STORAGE_LOCATION=./data/uploads

./mvnw spring-boot:run
```

Flyway creates the schema on first start; Hibernate runs with `ddl-auto=validate`, so a schema
drift fails fast instead of silently altering tables.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/examprep` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `examprep` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `examprep` | Database password |
| `GOOGLE_API_KEY` | empty | Gemini API key; AI features need it |
| `GEMINI_MODEL` | `gemini-3.8-flash` | Model used for generation and tutoring |
| `GEMINI_TIMEOUT_SECONDS` | `120` | Per-request timeout |
| `GEMINI_MAX_RETRIES` | `2` | Retries for rate limits and transient failures |
| `GEMINI_MAX_OUTPUT_TOKENS` | `8192` | Response size cap |
| `APP_STORAGE_LOCATION` | `./data/uploads` | Private document storage; must not be inside `src/main/resources/static` |
| `APP_MAX_UPLOAD_SIZE_MB` | `25` | Upload limit, applied by Spring and by the validator |
| `APP_STALE_JOB_MINUTES` | `30` | Age after which interrupted jobs are considered stale |
| `SERVER_PORT` | `8080` | HTTP port |

Chunk sizes, question and flashcard counts and the source-text ceiling per AI request are defined in
`src/main/resources/application.yml` under `app.processing` and `app.study`.

## How a document becomes study material

1. **Upload.** The controller accepts a multipart file, validates the extension, size and sniffed
   content type, streams it into private storage and records a document with status `UPLOADED`.
2. **Extraction.** A background task marks the document `PROCESSING`, extracts text with the
   format-specific parser (PDF pages, DOCX headings, PPTX slides, Markdown headings, Tika fallback),
   splits it into bounded chunks labelled `S1…Sn`, and marks the document `READY` or `FAILED` with a
   readable reason.
3. **Generation.** A study pack starts as `QUEUED`. A background task sends the extracted sections to
   Gemini with a strict JSON contract. Small documents are generated in one pass; larger documents
   are summarised section by section and then reduced into the final package. Invalid JSON triggers
   one repair attempt; invalid citations are removed.
4. **Study.** The package is stored as validated JSONB columns and served through dedicated pages and
   a small JSON API for asynchronous interactions (status polling, practice feedback, exam
   submission, tutor questions).

If the process stops mid-job, startup recovery marks still-open documents and study packs as failed
so the student can retry instead of waiting forever. This assumes a single application instance.

## Privacy and security

- Passwords are hashed with BCrypt; sessions are HttpOnly, SameSite=Lax cookies.
- Every page and API request requires authentication except the landing, login and registration
  pages.
- All state-changing requests use CSRF tokens, including the JSON endpoints.
- Ownership is enforced in the service layer by the authenticated user id. Another student's
  document or study pack returns `404`, never `403`.
- Uploads are validated by extension, configured size and sniffed content type, and stored with
  generated names outside the static web root. Client file names never become paths.
- API keys are read from the environment, never rendered into HTML or logged.
- Extracted text is sent to Google's Gemini API when you request study material or tutor answers.
  Nothing is sent during upload, and nothing is sent if no API key is configured.
- Prompts treat document text as untrusted content and instruct the model to ignore instructions
  inside it; generated JSON is validated before it is stored.

## Tests

```bash
./mvnw verify          # or: scripts/verify.sh
```

The suite runs against a real PostgreSQL binary started by `embedded-postgres`, so no Docker daemon,
no external database and no API key are required. AI calls are replaced with stubs and mocks; the
tests never make paid requests. See [docs/testing.md](docs/testing.md) for the inventory.

## Project layout

```
src/main/java/com/examprep
├── ai/          Gemini gateway, prompts, JSON validation, generation and tutoring
├── api/         Cross-module DTOs and application-service interfaces
├── common/      Shared exceptions and helpers
├── config/      Typed configuration, async executors, Jackson setup
├── document/    Upload validation, private storage, parsers, chunking, extraction worker
├── security/    Security configuration, principal, current-user lookup
├── study/       Study packs, questions, attempts, flashcards, progress, generation worker
├── user/        Accounts and registration
└── web/         Page controllers, JSON API, exception handlers, view formatting

src/main/resources
├── db/migration/   Flyway migrations (V1 users, V2 documents, V3 study and AI)
├── static/         CSS, vanilla JS, favicon
└── templates/      Thymeleaf pages and fragments
```

## Further documentation

- [Architecture](docs/architecture.md) — modules, data model, pipelines, failure handling.
- [Agent contracts](docs/agent-contracts.md) — the five functional agents, their inputs, outputs and
  invariants.
- [Testing](docs/testing.md) — coverage, how to run it, manual verification checklist.

## Known limitations

- **Single instance.** Startup recovery marks open jobs as failed, which is safe for one instance but
  not for a horizontally scaled deployment sharing a database.
- **No OCR.** Scanned PDFs that contain images of text are rejected with an explanatory message.
- **Tutor retrieval is lexical** (weighted keyword overlap), not embedding based. It is deterministic
  and needs no vector database, but it can miss paraphrases.
- **Docker was not executed in the development environment** used to build this project (the daemon
  socket was unavailable), so the image and Compose stack are delivered but were not run there. The
  application itself was verified with Maven and an embedded PostgreSQL instance.
- **Real Gemini responses were not exercised** because no API key and no paid calls were available.
  The integration is covered by contract tests with stubbed transports.
