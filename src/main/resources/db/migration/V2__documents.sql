-- ExamPrep: uploaded learning material and extracted chunks
CREATE TABLE documents (
    id                UUID PRIMARY KEY,
    owner_id          UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    original_filename VARCHAR(255) NOT NULL,
    content_type      VARCHAR(200) NOT NULL,
    file_extension    VARCHAR(20)  NOT NULL,
    storage_key       VARCHAR(255) NOT NULL,
    file_size         BIGINT       NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    failure_reason    VARCHAR(1000),
    page_count        INTEGER,
    char_count        INTEGER,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_documents_owner_created ON documents (owner_id, created_at DESC);

CREATE TABLE document_chunks (
    id             UUID PRIMARY KEY,
    document_id    UUID         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    ref_id         VARCHAR(40)  NOT NULL,
    ordinal        INTEGER      NOT NULL,
    location_label VARCHAR(200) NOT NULL,
    content        TEXT         NOT NULL,
    char_count     INTEGER      NOT NULL
);

CREATE UNIQUE INDEX ux_document_chunks_ref ON document_chunks (document_id, ref_id);
CREATE INDEX ix_document_chunks_document_ordinal ON document_chunks (document_id, ordinal);
