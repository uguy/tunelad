--liquibase formatted sql

--changeset tunelad:set-sets
-- tunes_key is the ordered tune ids joined by commas: its unique index keeps one set per distinct ordered list.
-- created_by and tune_id refer to the account and tune modules by value: no foreign key across modules.
CREATE TABLE set_sets
(
    id         TEXT PRIMARY KEY,
    tunes_key  TEXT                     NOT NULL UNIQUE,
    created_by TEXT                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE set_set_tunes
(
    set_id   TEXT    NOT NULL REFERENCES set_sets (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    tune_id  TEXT    NOT NULL,
    PRIMARY KEY (set_id, position)
);
CREATE INDEX set_set_tunes_tune_idx ON set_set_tunes (tune_id);
