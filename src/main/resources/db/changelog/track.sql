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
