-- ExamPrep: generated study packages, practice material, assessment attempts and tutor conversations.

CREATE TABLE study_pack (
    id                        UUID PRIMARY KEY,
    owner_id                  UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    document_id               UUID         NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    title                     VARCHAR(300) NOT NULL,
    status                    VARCHAR(20)  NOT NULL,
    progress_message          VARCHAR(300),
    failure_reason            VARCHAR(500),
    language                  VARCHAR(20)  NOT NULL,
    academic_level            VARCHAR(40)  NOT NULL,
    exam_date                 DATE,
    requested_question_count  INTEGER      NOT NULL,
    requested_flashcard_count INTEGER      NOT NULL,
    exam_duration_minutes     INTEGER      NOT NULL,
    introduction              TEXT,
    summary                   TEXT,
    learning_objectives       JSONB,
    revision_priorities       JSONB,
    topics                    JSONB,
    revision_notes            JSONB,
    definitions               JSONB,
    formulas                  JSONB,
    key_facts                 JSONB,
    created_at                TIMESTAMPTZ  NOT NULL,
    updated_at                TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_study_pack_status CHECK (status IN ('QUEUED', 'PROCESSING', 'READY', 'FAILED'))
);

CREATE INDEX ix_study_pack_owner ON study_pack (owner_id, created_at DESC);
CREATE INDEX ix_study_pack_document ON study_pack (document_id);
CREATE INDEX ix_study_pack_status ON study_pack (status);

CREATE TABLE flashcard (
    id               UUID PRIMARY KEY,
    study_pack_id    UUID         NOT NULL REFERENCES study_pack (id) ON DELETE CASCADE,
    position         INTEGER      NOT NULL,
    front            TEXT         NOT NULL,
    back             TEXT         NOT NULL,
    source_reference VARCHAR(160),
    CONSTRAINT ux_flashcard_position UNIQUE (study_pack_id, position)
);

CREATE INDEX ix_flashcard_pack ON flashcard (study_pack_id, position);

CREATE TABLE question (
    id               UUID PRIMARY KEY,
    study_pack_id    UUID         NOT NULL REFERENCES study_pack (id) ON DELETE CASCADE,
    position         INTEGER      NOT NULL,
    question_type    VARCHAR(20)  NOT NULL,
    prompt           TEXT         NOT NULL,
    options          JSONB,
    correct_answer   TEXT         NOT NULL,
    explanation      TEXT         NOT NULL,
    difficulty       VARCHAR(20)  NOT NULL,
    source_reference VARCHAR(160),
    exam_eligible    BOOLEAN      NOT NULL,
    CONSTRAINT ck_question_type CHECK (question_type IN ('MULTIPLE_CHOICE', 'TRUE_FALSE', 'SHORT_ANSWER')),
    CONSTRAINT ux_question_position UNIQUE (study_pack_id, position)
);

CREATE INDEX ix_question_pack ON question (study_pack_id, position);

CREATE TABLE quiz_attempt (
    id                 UUID PRIMARY KEY,
    study_pack_id      UUID        NOT NULL REFERENCES study_pack (id) ON DELETE CASCADE,
    student_id         UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    mode               VARCHAR(20) NOT NULL,
    score              INTEGER     NOT NULL,
    total_questions    INTEGER     NOT NULL,
    time_taken_seconds INTEGER     NOT NULL,
    submitted_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_quiz_attempt_mode CHECK (mode IN ('MOCK_EXAM'))
);

CREATE INDEX ix_quiz_attempt_student ON quiz_attempt (student_id, submitted_at DESC);
CREATE INDEX ix_quiz_attempt_pack ON quiz_attempt (study_pack_id);

CREATE TABLE quiz_answer (
    id           UUID PRIMARY KEY,
    attempt_id   UUID    NOT NULL REFERENCES quiz_attempt (id) ON DELETE CASCADE,
    question_id  UUID    NOT NULL REFERENCES question (id) ON DELETE CASCADE,
    given_answer TEXT,
    correct      BOOLEAN NOT NULL
);

CREATE INDEX ix_quiz_answer_attempt ON quiz_answer (attempt_id);

CREATE TABLE tutor_message (
    id                UUID PRIMARY KEY,
    study_pack_id     UUID        NOT NULL REFERENCES study_pack (id) ON DELETE CASCADE,
    student_id        UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role              VARCHAR(16) NOT NULL,
    content           TEXT        NOT NULL,
    source_references JSONB,
    created_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_tutor_message_role CHECK (role IN ('USER', 'ASSISTANT'))
);

CREATE INDEX ix_tutor_message_pack ON tutor_message (study_pack_id, created_at);

CREATE TABLE flashcard_review (
    id            UUID PRIMARY KEY,
    study_pack_id UUID        NOT NULL REFERENCES study_pack (id) ON DELETE CASCADE,
    student_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    flashcard_id  UUID        NOT NULL REFERENCES flashcard (id) ON DELETE CASCADE,
    reviewed_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_flashcard_review_student ON flashcard_review (student_id, reviewed_at DESC);
