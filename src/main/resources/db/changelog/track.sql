--liquibase formatted sql

--changeset tunelad:track-tracks
-- owner_id refers to an account of the account module by value: no foreign key across modules
CREATE TABLE track_tracks
(
    id                 TEXT   NOT NULL,
    version            INT    NOT NULL,
    title              TEXT,
    description        TEXT,
    artist_name        TEXT,
    album              TEXT,
    art_url            TEXT,
    format             TEXT,
    owner_id           TEXT,
    owner_display_name TEXT,
    tags               TEXT[] NOT NULL DEFAULT '{}',
    PRIMARY KEY (id)
);
CREATE INDEX track_tracks_tags_idx ON track_tracks USING gin (tags);

--changeset tunelad:track-artists
-- An Artist is shared by the tracks naming it, matched ignoring case and surrounding whitespace.
CREATE TABLE track_artists
(
    id   TEXT NOT NULL,
    name TEXT NOT NULL,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX track_artists_name_idx ON track_artists (lower(name));

INSERT INTO track_artists (id, name)
SELECT gen_random_uuid()::text, trimmed
FROM (SELECT DISTINCT ON (lower(trimmed)) trimmed
      FROM (SELECT trim(artist_name) AS trimmed FROM track_tracks) names
      WHERE trimmed <> ''
      ORDER BY lower(trimmed), trimmed) distinct_names;

ALTER TABLE track_tracks
    ADD COLUMN artist_id TEXT REFERENCES track_artists (id);
UPDATE track_tracks
SET artist_id = (SELECT a.id FROM track_artists a WHERE lower(a.name) = lower(trim(track_tracks.artist_name)));
ALTER TABLE track_tracks
    DROP COLUMN artist_name;
--rollback ALTER TABLE track_tracks ADD COLUMN artist_name TEXT;
--rollback UPDATE track_tracks SET artist_name = (SELECT a.name FROM track_artists a WHERE a.id = track_tracks.artist_id);
--rollback ALTER TABLE track_tracks DROP COLUMN artist_id;
--rollback DROP TABLE track_artists;

--changeset tunelad:track-performances
-- A Track records one Set; set_id and tune_id refer to the set and tune modules by value: no foreign key across modules.
-- Tracks stored before Sets existed keep a null set_id and have no Performances.
ALTER TABLE track_tracks
    ADD COLUMN set_id TEXT;

CREATE TABLE track_performances
(
    track_id             TEXT           NOT NULL REFERENCES track_tracks (id) ON DELETE CASCADE,
    position             INTEGER        NOT NULL,
    tune_id              TEXT           NOT NULL,
    start_offset_seconds NUMERIC(9, 3)  NOT NULL DEFAULT 0,
    key                  TEXT,
    notes                TEXT,
    PRIMARY KEY (track_id, position)
);
CREATE INDEX track_performances_tune_idx ON track_performances (tune_id);
--rollback DROP TABLE track_performances;
--rollback ALTER TABLE track_tracks DROP COLUMN set_id;

--changeset tunelad:track-performance-transcription
-- The Transcription of a Performance: ABC notation, null until an Account writes one.
ALTER TABLE track_performances
    ADD COLUMN abc TEXT;
--rollback ALTER TABLE track_performances DROP COLUMN abc;

--changeset tunelad:track-performance-identification
-- The Identification state of a Performance: 'pending' (nothing proposed), 'identified' (proposed from the audio) or
-- 'corrected' (edited by an Account). Performances stored before Identification existed count as corrected.
ALTER TABLE track_performances
    ADD COLUMN identification TEXT NOT NULL DEFAULT 'corrected'
        CONSTRAINT track_performances_identification_check CHECK (identification IN ('pending', 'identified', 'corrected'));
--rollback ALTER TABLE track_performances DROP COLUMN identification;

--changeset tunelad:track-performance-nothing-found
-- A fourth Identification state: an analysis finished without hearing the Performance's Tune.
ALTER TABLE track_performances DROP CONSTRAINT track_performances_identification_check;
ALTER TABLE track_performances
    ADD CONSTRAINT track_performances_identification_check
        CHECK (identification IN ('pending', 'identified', 'nothing_found', 'corrected'));
--rollback UPDATE track_performances SET identification = 'pending' WHERE identification = 'nothing_found';
--rollback ALTER TABLE track_performances DROP CONSTRAINT track_performances_identification_check;
--rollback ALTER TABLE track_performances ADD CONSTRAINT track_performances_identification_check CHECK (identification IN ('pending', 'identified', 'corrected'));
