CREATE TABLE lotto_round (
    round_no         INT          NOT NULL,
    n1               INT          NOT NULL,
    n2               INT          NOT NULL,
    n3               INT          NOT NULL,
    n4               INT          NOT NULL,
    n5               INT          NOT NULL,
    n6               INT          NOT NULL,
    bonus            INT          NOT NULL,
    draw_date        DATE         NOT NULL,
    rank1_count      INT          NOT NULL,
    rank2_count      INT          NOT NULL,
    rank3_count      INT          NOT NULL,
    rank4_count      INT          NOT NULL,
    rank5_count      INT          NOT NULL,
    rank1_amount     BIGINT       NOT NULL,
    rank2_amount     BIGINT       NOT NULL,
    rank3_amount     BIGINT       NOT NULL,
    rank4_amount     BIGINT       NOT NULL,
    rank5_amount     BIGINT       NOT NULL,
    source           VARCHAR(255) NOT NULL,
    fetched_at       DATETIME(6)  NOT NULL,
    store_fetched_at DATETIME(6)  NULL,
    PRIMARY KEY (round_no)
);

CREATE TABLE winning_store (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    round_no      INT          NOT NULL,
    win_rank      INT          NOT NULL,
    region        VARCHAR(100) NOT NULL,
    name          VARCHAR(255) NOT NULL,
    address       VARCHAR(500) NOT NULL,
    purchase_type VARCHAR(50)  NULL,
    latitude      DOUBLE       NULL,
    longitude     DOUBLE       NULL,
    status        VARCHAR(50)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_winning_store_round FOREIGN KEY (round_no) REFERENCES lotto_round (round_no)
);

CREATE INDEX idx_winning_store_round_rank ON winning_store (round_no, win_rank);
