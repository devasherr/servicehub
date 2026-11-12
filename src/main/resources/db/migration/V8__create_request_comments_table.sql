CREATE TABLE request_comments (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id BIGINT      NOT NULL REFERENCES service_requests (id) ON DELETE CASCADE,
    author_id  BIGINT      NOT NULL REFERENCES users (id),
    content    TEXT        NOT NULL,
    internal   BOOLEAN     NOT NULL DEFAULT FALSE,  -- TRUE = staff-only work note
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_comments_request ON request_comments (request_id, created_at);
