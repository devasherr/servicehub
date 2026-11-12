CREATE TABLE request_status_history (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id  BIGINT      NOT NULL REFERENCES service_requests (id) ON DELETE CASCADE,
    from_status VARCHAR(20),                         -- NULL for the initial SUBMITTED entry
    to_status   VARCHAR(20) NOT NULL,
    changed_by  BIGINT      NOT NULL REFERENCES users (id),
    note        VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_history_from CHECK (from_status IS NULL OR from_status IN
        ('SUBMITTED', 'PENDING', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    CONSTRAINT chk_history_to CHECK (to_status IN
        ('SUBMITTED', 'PENDING', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'))
);

CREATE INDEX idx_history_request ON request_status_history (request_id, created_at);
