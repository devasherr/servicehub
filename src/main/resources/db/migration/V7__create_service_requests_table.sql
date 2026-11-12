-- Used by the service layer to build references like SR-2026-0001
CREATE SEQUENCE service_request_ref_seq START 1;

CREATE TABLE service_requests (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference_number VARCHAR(20)  NOT NULL UNIQUE,
    title            VARCHAR(200) NOT NULL,
    description      TEXT         NOT NULL,
    category_id      BIGINT       NOT NULL REFERENCES service_categories (id),
    priority         VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    status           VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED',
    location         VARCHAR(150),
    requester_id     BIGINT       NOT NULL REFERENCES users (id),
    department_id    BIGINT       NOT NULL REFERENCES departments (id),
    assigned_to      BIGINT       REFERENCES users (id),
    resolved_at      TIMESTAMPTZ,
    closed_at        TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_request_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_request_status   CHECK (status IN
        ('SUBMITTED', 'PENDING', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'))
);

CREATE INDEX idx_requests_status     ON service_requests (status);
CREATE INDEX idx_requests_priority   ON service_requests (priority);
CREATE INDEX idx_requests_requester  ON service_requests (requester_id);
CREATE INDEX idx_requests_assignee   ON service_requests (assigned_to);
CREATE INDEX idx_requests_department ON service_requests (department_id);
CREATE INDEX idx_requests_category   ON service_requests (category_id);
CREATE INDEX idx_requests_created_at ON service_requests (created_at);
