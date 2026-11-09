-- ExamPrep: student accounts.
CREATE TABLE app_user (
    id            UUID PRIMARY KEY,
    full_name     VARCHAR(120) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX ux_app_user_email ON app_user (email);
