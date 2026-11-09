-- 마지막 시뮬레이션 결과 한 행만 둔다(id = 1). 전략별 집계는 JSON으로 저장한다.
CREATE TABLE simulation_result (
    id          INT         NOT NULL,
    from_round  INT         NOT NULL,
    to_round    INT         NOT NULL,
    repetitions INT         NOT NULL,
    strategies  TEXT        NOT NULL,
    computed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);
