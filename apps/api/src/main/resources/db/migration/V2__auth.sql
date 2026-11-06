CREATE TABLE auth_session (
    token_hash VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (token_hash)
);

CREATE TABLE auth_state (
    id                 INT         NOT NULL,
    failed_count       INT         NOT NULL,
    locked_until       DATETIME(6) NULL,
    last_used_step     BIGINT      NOT NULL,
    secret_fingerprint VARCHAR(64) NULL,
    PRIMARY KEY (id)
);

INSERT INTO auth_state (id, failed_count, locked_until, last_used_step, secret_fingerprint)
VALUES (1, 0, NULL, -1, NULL);
