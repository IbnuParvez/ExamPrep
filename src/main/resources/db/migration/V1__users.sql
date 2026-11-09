-- ExamPrep: student accounts
CREATE TABLE users (
    id            UUID PRIMARY KEY,
    full_name     VARCHAR(120) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Emails are compared case-insensitively, so uniqueness must be enforced the same way.
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));
