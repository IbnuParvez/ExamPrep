# ExamPrep

ExamPrep is a browser-based AI examination preparation platform. A student uploads course material
(lecture notes, handouts, slide decks, textbooks), the application extracts the text with truthful page,
slide or section references, and a self-hosted FreeLLMAPI router turns it into a structured study package:
summary, topics,
revision notes, definitions, formulas, flashcards, practice questions, a timed mock examination and a
document-grounded tutor.

The application is a **modular monolith**: Spring Boot 3.5 serves both the browser interface (Thymeleaf)
and all business logic. There is no separate JavaScript frontend, no Node runtime and no microservices.

---

## Technology stack

| Concern | Technology |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.5.16 |
| Architecture | Modular monolith (`com.examprep.{user,document,ai,study,web}`) |
| Browser interface | Thymeleaf 3, HTML5, CSS3, vanilla JavaScript |
| Persistence | Spring Data JPA / Hibernate 6, PostgreSQL 16+, Flyway migrations |
| Authentication | Spring Security (session-based, BCrypt, CSRF) |
| AI | Self-hosted [FreeLLMAPI](https://github.com/tashfeenahmed/freellmapi) OpenAI-compatible router (`java.net.http.HttpClient` + Jackson) |
| Document extraction | Apache Tika 3, Apache PDFBox 3, Apache POI 5 (structural boundaries) |
| Build | Maven (wrapper included) |
| Tests | JUnit 5, Mockito, Spring Boot Test, MockMvc, embedded PostgreSQL |
| Deployment | Docker multi-stage image (non-root), Docker Compose |

---

## Implemented features

**Accounts and security**

- Student registration with validation, BCrypt password hashing and automatic sign-in.
- Email/password login, logout, session fixation protection and CSRF on every state-changing request.
- Per-student isolation: documents, study packages, attempts and tutor conversations are scoped to the
  authenticated account; a foreign ID returns "not found" rather than exposing another student's data.
- Private upload storage outside the web root, generated internal filenames, content-type detection.

**Documents**

- Upload via file picker or drag-and-drop with client and server validation, progress and status polling.
- Supports PDF, DOCX, DOC, PPTX, PPT, TXT, Markdown, RTF, ODT and ODP (25 MB default limit, configurable).
- PDF extraction is page-aware (PDFBox); DOCX/PPTX/DOC/PPT keep real section and slide boundaries (POI);
  other formats use Tika.
- Asynchronous extraction into stable, citable chunks; scanned/image-only PDFs fail with a clear
  explanation instead of empty notes.
- Document deletion removes the file, chunks and all dependent study data.

**AI study engine**

- The AI router is called only from Java, through a gateway interface; the proxy key never reaches the
  browser.
- Configurable routing (`FREELLMAPI_MODEL`, default `auto`), timeouts, retry count and concurrency limit.
- Long documents are sampled evenly across their whole length, not just their opening pages.
- Structured JSON output validated into typed records; malformed output gets one repair attempt, then fails
  loudly. No fake study material is ever stored.
- Large documents are analysed in sections (hierarchical summarisation) instead of one giant request.
- Citations are returned as chunk reference IDs and validated against the real chunks; fabricated page
  numbers or references are discarded.
- Uploaded document text is treated as untrusted content: prompt-injection attempts in the material are
  explicitly downgraded to study content.

**Study package**

- Overview, introduction, summary, learning objectives and high-value revision areas.
- Topics with explanations, key concepts and sources; revision notes; definitions; formulas/key facts.
- Interactive flashcards (reveal, previous/next, keyboard support) with review recording.
- Practice questions (multiple choice, true/false, short answer) with answers revealed on request only.
- Timed mock examination: randomised questions, question navigator, countdown, submission confirmation,
  automatic marking, score, per-question review and revision areas. Correct answers are never sent to the
  browser before submission.
- AI tutor per package: lexical retrieval of the most relevant chunks, grounded answers with validated
  sources, an explicit "not enough information" signal, and persisted conversation history.
- Simple progress view: document/package counts, completed exams, scores, best and average score.

---

## Quick start with Docker Compose

1. Copy the environment template and fill it in:

   ```bash
   cp .env.example .env
   # edit .env — at minimum set POSTGRES_PASSWORD and FREELLMAPI_API_KEY
   ```

2. Build and start:

   ```bash
   docker compose up --build
   ```

3. Open <http://localhost:8080>, create an account and upload a document.

The database and uploaded files live in named volumes (`examprep-db`, `examprep-uploads`), so restarting
the containers does not lose data. The application image runs as a non-root user and contains no secrets.

The application **starts without a router key**. Extraction works, but study generation and the tutor
report a clear configuration error until `FREELLMAPI_API_KEY` is set.

Inside the container the app reaches the host's router at `http://host.docker.internal:3001/v1`
(`extra_hosts` maps it on Linux Docker Engine), while your local `.env` keeps
`FREELLMAPI_BASE_URL=http://localhost:3001/v1` for `./mvnw spring-boot:run`. The same `.env` works for both.

---

## Local development (without Docker)

### 1. Java 21

Install a JDK 21 (Temurin, OpenJDK or similar) and confirm:

```bash
java -version   # must report 21.x
```

### 2. PostgreSQL

Create a database and user:

```sql
CREATE USER examprep WITH PASSWORD 'change-me';
CREATE DATABASE examprep OWNER examprep;
```

Flyway creates every table on startup; Hibernate runs in `validate` mode only.

### 3. FreeLLMAPI router

Run the self-hosted [tashfeenahmed/freellmapi](https://github.com/tashfeenahmed/freellmapi) router on the
host (default port 3001), enable at least one provider key/model in its dashboard, and copy the unified
proxy key (Keys → Agents). Set `FREELLMAPI_API_KEY` and `FREELLMAPI_BASE_URL` in your environment (or in
`.env`, see below). Never commit the key.

### 4. Environment variables

```bash
cp .env.example .env
# edit .env
set -a; source .env; set +a
```

Spring Boot reads environment variables, not `.env` files, so the `source` step is required when running
locally. Docker Compose reads `.env` automatically.

| Variable | Purpose | Default |
| --- | --- | --- |
| `FREELLMAPI_BASE_URL` | Router base URL (the app appends `/v1` when the path is empty) | `http://localhost:3001/v1` |
| `FREELLMAPI_API_KEY` | Unified proxy key from the FreeLLMAPI dashboard | *(empty — AI disabled)* |
| `FREELLMAPI_MODEL` | Routing mode or pinned model ID (`auto`, `auto:smart`, `auto:fast`, `auto:reliable`, `auto:balanced`, `fusion`, …) | `auto` |
| `FREELLMAPI_CONNECT_TIMEOUT_SECONDS` | TCP/TLS connect timeout | `10` |
| `FREELLMAPI_TIMEOUT_SECONDS` | Per-request timeout for background generation | `180` |
| `FREELLMAPI_TUTOR_TIMEOUT_SECONDS` | Per-request timeout for synchronous tutor calls | `60` |
| `FREELLMAPI_MAX_RETRIES` | Retries for transient 408/5xx failures, timeouts and empty completions | `2` |
| `FREELLMAPI_MAX_CONCURRENCY` | Maximum simultaneous router requests | `3` |
| `FREELLMAPI_REASONING_EFFORT` | Optional reasoning hint forwarded to providers that support it | `low` |
| `SPRING_DATASOURCE_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/examprep` |
| `SPRING_DATASOURCE_USERNAME` | Database user | `examprep` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `examprep` |
| `APP_STORAGE_LOCATION` | Private directory for uploads | `./data/uploads` |
| `APP_MAX_UPLOAD_SIZE_MB` | Maximum upload size | `25` |
| `SERVER_PORT` | HTTP port | `8080` |

### 5. Run

```bash
./mvnw spring-boot:run
```

Open <http://localhost:8080>.

### 6. Run the tests

```bash
./mvnw clean verify
```

Tests start their own PostgreSQL from embedded binaries, so **no local database and no FreeLLMAPI key are
required**. See [docs/testing.md](docs/testing.md) for coverage details and limitations.

---

## Project layout

```
src/main/java/com/examprep
├── ExamPrepApplication.java
├── api/            shared application contracts, DTOs and enums
├── config/         typed configuration properties, async executors
├── security/       Spring Security configuration, principal, current-user resolution
├── user/           account entity, repository, registration
├── common/         application exceptions
├── document/       upload validation, private storage, extraction, chunking, async pipeline
├── ai/             FreeLLMAPI client/gateway, prompts, structured generation, retrieval, sanitisation
├── study/          study packages, flashcards, questions, attempts, tutor, progress
└── web/            Thymeleaf controllers, JSON endpoints, forms, error handling

src/main/resources
├── templates/      layout, landing, auth, dashboard, documents, study, exam, errors
├── static/         CSS design system and vanilla JavaScript
└── db/migration/   Flyway: V1 users, V2 documents/chunks, V3 study/AI
```

---

## Configuration notes

- Uploaded files are stored under `APP_STORAGE_LOCATION` and are never served by a URL. The directory must
  not be inside `src/main/resources/static`.
- `APP_MAX_UPLOAD_SIZE_MB` also raises the servlet multipart limit; if you change it, restart the app.
- Background work uses bounded thread pools (`examprep.processing.*`). Jobs interrupted by a restart are
  marked failed with an actionable message and can be retried from the document page.
- Study generation and extraction are asynchronous; the browser polls persisted status and never shows a
  success message before the work actually completes.

---

## Security summary

- BCrypt password hashes, session-based authentication, CSRF protection, HTTP-only session cookies.
- Every user-specific operation is scoped by owner ID at the data-access level.
- Uploads validated by extension, size and detected content; filenames sanitised; storage paths generated
  and protected against traversal.
- API key read from the environment only, never logged, rendered or returned.
- AI output is validated and sanitised; citations that do not exist in the source are dropped.
- Correct exam answers are absent from exam DTOs and HTML until the attempt is submitted.
- Document contents and tutor conversations are not written to application logs.

---

## Privacy

Extracted document text is processed by the administrator's self-hosted FreeLLMAPI router, which forwards it
to the configured model provider, to generate study material and answer tutor questions. Files are stored privately and can be deleted at any time; deleting a document also deletes its
study packages, attempts and tutor history. The in-app privacy notice is served at `/privacy`.

---

## Known limitations

- **No OCR.** Scanned PDFs and image-only documents cannot be read. The application detects this and
  explains it rather than generating empty material.
- **AI output can be wrong.** Generated material is educational assistance; verify important facts against
  the official course material. Content is labelled accordingly in the interface.
- **Lexical retrieval only.** The tutor selects source chunks by term overlap; there is no vector database
  in this release, as specified.
- **English-oriented prompts.** `language` is passed to the model, but prompt templates and interface copy
  are English.
- **Real router calls are not part of automated tests.** They use mocked gateway responses; a live smoke
  test requires the project owner's FreeLLMAPI proxy key.
- **Short-answer questions are practice-only.** Mock exams use multiple-choice and true/false questions so
  scoring is fully automatic.
- The five workstreams described in the development brief were delivered as one integrated change set on
  the default branch rather than five long-lived branches with pull requests; see
  [docs/agent-contracts.md](docs/agent-contracts.md) for the ownership mapping and contract record.

---

## Documentation

- [Architecture](docs/architecture.md)
- [Agent contracts and module ownership](docs/agent-contracts.md)
- [Testing](docs/testing.md)
.
