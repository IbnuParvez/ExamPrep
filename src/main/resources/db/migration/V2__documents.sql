-- ExamPrep: uploaded study documents and their extracted chunks.

CREATE TABLE document (
    id                UUID PRIMARY KEY,
    owner_id          UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    original_filename VARCHAR(255) NOT NULL,
    content_type      VARCHAR(160) NOT NULL,
    file_extension    VARCHAR(16)  NOT NULL,
    storage_key       VARCHAR(320) NOT NULL,
    file_size_bytes   BIGINT       NOT NULL,
    page_count        INTEGER,
    character_count   INTEGER,
    status            VARCHAR(20)  NOT NULL,
    failure_reason    VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_document_status CHECK (status IN ('UPLOADED', 'PROCESSING', 'READY', 'FAILED'))
);

CREATE INDEX ix_document_owner ON document (owner_id, created_at DESC);
CREATE INDEX ix_document_status ON document (status);

CREATE TABLE document_chunk (
    id              UUID PRIMARY KEY,
    document_id     UUID         NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    chunk_index     INTEGER      NOT NULL,
    reference_id    VARCHAR(16)  NOT NULL,
    location_label  VARCHAR(160) NOT NULL,
    heading         VARCHAR(300),
    content         TEXT         NOT NULL,
    character_count INTEGER      NOT NULL,
    CONSTRAINT ux_document_chunk_reference UNIQUE (document_id, reference_id),
    CONSTRAINT ux_document_chunk_order UNIQUE (document_id, chunk_index)
);

CREATE INDEX ix_document_chunk_document ON document_chunk (document_id, chunk_index);
