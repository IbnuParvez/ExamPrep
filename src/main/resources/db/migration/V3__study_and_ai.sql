-- ExamPrep: generated study packages, practice material, mock exams and the AI tutor
CREATE TABLE study_packs (
    id               UUID PRIMARY KEY,
    owner_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    document_id      UUID         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    title            VARCHAR(300) NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    failure_reason   VARCHAR(1000),
    progress_message VARCHAR(300),
    language         VARCHAR(40),
    academic_level   VARCHAR(60),
    exam_date        DATE,
    question_target  INTEGER,
    flashcard_target INTEGER,
    exam_question_count  INTEGER,
    exam_duration_minutes INTEGER,
    progress_percent INTEGER      NOT NULL DEFAULT 0,
    introduction     TEXT,
    summary          TEXT,
    objectives       JSONB,
    revision_areas   JSONB,
    content          JSONB,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_study_packs_owner_created ON study_packs (owner_id, created_at DESC);
CREATE INDEX ix_study_packs_document ON study_packs (document_id);

CREATE TABLE flashcards (
    id            UUID PRIMARY KEY,
    study_pack_id UUID    NOT NULL REFERENCES study_packs (id) ON DELETE CASCADE,
    ordinal       INTEGER NOT NULL,
    front         TEXT    NOT NULL,
    back          TEXT    NOT NULL,
    source_ref    VARCHAR(200)
);

CREATE INDEX ix_flashcards_pack_ordinal ON flashcards (study_pack_id, ordinal);

CREATE TABLE questions (
    id                   UUID PRIMARY KEY,
    study_pack_id        UUID        NOT NULL REFERENCES study_packs (id) ON DELETE CASCADE,
    ordinal              INTEGER     NOT NULL,
    type                 VARCHAR(30) NOT NULL,
    question_text        TEXT        NOT NULL,
    options              JSONB,
    correct_option_index INTEGER,
    correct_boolean      BOOLEAN,
    answer_text          TEXT,
    explanation          TEXT,
    difficulty           VARCHAR(20) NOT NULL,
    source_ref           VARCHAR(200),
    exam_eligible        BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE INDEX ix_questions_pack_ordinal ON questions (study_pack_id, ordinal);

CREATE TABLE quiz_attempts (
    id                 UUID PRIMARY KEY,
    owner_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    study_pack_id      UUID        NOT NULL REFERENCES study_packs (id) ON DELETE CASCADE,
    kind               VARCHAR(20) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    score              INTEGER     NOT NULL DEFAULT 0,
    total_questions    INTEGER     NOT NULL DEFAULT 0,
    duration_seconds   INTEGER     NOT NULL DEFAULT 900,
    time_taken_seconds INTEGER,
    started_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at       TIMESTAMPTZ
);

CREATE INDEX ix_quiz_attempts_owner_submitted ON quiz_attempts (owner_id, submitted_at DESC);

CREATE TABLE quiz_attempt_answers (
    id                   UUID PRIMARY KEY,
    attempt_id           UUID    NOT NULL REFERENCES quiz_attempts (id) ON DELETE CASCADE,
    question_id          UUID    NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    position             INTEGER NOT NULL,
    selected_option_index INTEGER,
    boolean_answer       BOOLEAN,
    text_answer          TEXT,
    correct              BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX ux_quiz_attempt_answers_question ON quiz_attempt_answers (attempt_id, question_id);

CREATE TABLE tutor_messages (
    id            UUID PRIMARY KEY,
    owner_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    study_pack_id UUID        NOT NULL REFERENCES study_packs (id) ON DELETE CASCADE,
    role          VARCHAR(20) NOT NULL,
    content       TEXT        NOT NULL,
    source_refs   JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_tutor_messages_pack_created ON tutor_messages (study_pack_id, created_at);

CREATE TABLE flashcard_reviews (
    id             UUID PRIMARY KEY,
    owner_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    study_pack_id  UUID        NOT NULL REFERENCES study_packs (id) ON DELETE CASCADE,
    reviewed_cards INTEGER     NOT NULL,
    total_cards    INTEGER     NOT NULL,
    reviewed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_flashcard_reviews_owner ON flashcard_reviews (owner_id, reviewed_at DESC);
