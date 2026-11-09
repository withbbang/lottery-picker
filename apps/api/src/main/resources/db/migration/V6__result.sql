CREATE TABLE recommendation_result (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    recommendation_id BIGINT      NOT NULL,
    round_no          INT         NOT NULL,
    match_count       INT         NOT NULL,
    prize_rank        VARCHAR(10) NOT NULL,
    prize_amount      BIGINT      NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_recommendation_result_recommendation UNIQUE (recommendation_id),
    CONSTRAINT fk_recommendation_result_recommendation
        FOREIGN KEY (recommendation_id) REFERENCES daily_recommendation (id)
);

CREATE INDEX idx_recommendation_result_round ON recommendation_result (round_no);
CREATE INDEX idx_daily_recommendation_round ON daily_recommendation (round_no);
