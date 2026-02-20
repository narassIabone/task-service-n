CREATE TABLE us.task (
                         id          UUID PRIMARY KEY,
                         title       TEXT         NOT NULL,
                         description TEXT,
                         status      VARCHAR(20)  NOT NULL,
                         assignee    VARCHAR(100) NOT NULL,
                         created_at  TIMESTAMPTZ  NOT NULL,
                         updated_at  TIMESTAMPTZ  NOT NULL,
                         notificate_at TIMESTAMPTZ NOT NULL,

                         CONSTRAINT check_status CHECK (status IN ('CREATED', 'DONE', 'ERROR'))
);