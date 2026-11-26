--liquibase formatted sql

--changeset tunelad:media-audio
-- Audio is kept in chunks, one row each, so that a byte range reads only the chunks that cover it.
-- file_key is the key chosen by the caller (the track id): no foreign key to the track module.
CREATE TABLE media_files
(
    file_key     TEXT   NOT NULL,
    length       BIGINT NOT NULL,
    chunk_size   INT    NOT NULL,
    content_type TEXT   NOT NULL,
    PRIMARY KEY (file_key)
);

CREATE TABLE media_chunks
(
    file_key TEXT  NOT NULL REFERENCES media_files (file_key) ON DELETE CASCADE,
    n        INT   NOT NULL,
    data     BYTEA NOT NULL,
    PRIMARY KEY (file_key, n)
);

CREATE TABLE media_waveforms
(
    file_key    TEXT                     NOT NULL REFERENCES media_files (file_key) ON DELETE CASCADE,
    status      TEXT                     NOT NULL CHECK (status IN ('PENDING', 'READY', 'FAILED')),
    peaks       DOUBLE PRECISION[]       NOT NULL DEFAULT '{}',
    computed_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (file_key)
);
