--liquibase formatted sql

--changeset tunelad:tune-tunes
-- created_by refers to the account module by value: no foreign key across modules.
-- source_id is the thesession.org tune id; null for a tune created by hand. Its unique index makes imports idempotent.
CREATE TABLE tune_tunes
(
    id            TEXT PRIMARY KEY,
    source_id     INTEGER UNIQUE,
    name          TEXT                     NOT NULL,
    aliases       TEXT[]                   NOT NULL DEFAULT '{}',
    author        TEXT,
    dance_type    TEXT                     NOT NULL CHECK (dance_type IN ('jig', 'reel', 'hornpipe', 'slide', 'polka', 'slip jig',
                                                                          'waltz', 'barndance', 'strathspey', 'march', 'mazurka', 'three-two')),
    usual_key     TEXT,
    reference_abc TEXT,
    created_by    TEXT                     NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX tune_tunes_name_idx ON tune_tunes (lower(name));
