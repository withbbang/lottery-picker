CREATE TABLE notification_log (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    kind       VARCHAR(30) NOT NULL,
    target_key VARCHAR(50) NOT NULL,
    status     VARCHAR(10) NOT NULL,
    attempts   INT         NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_log_target UNIQUE (kind, target_key)
);
