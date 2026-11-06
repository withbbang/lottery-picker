CREATE TABLE recommendation_settings (
    id               INT          NOT NULL,
    fixed_numbers    VARCHAR(200) NOT NULL,
    excluded_numbers VARCHAR(200) NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO recommendation_settings (id, fixed_numbers, excluded_numbers)
VALUES (1, '', '');
