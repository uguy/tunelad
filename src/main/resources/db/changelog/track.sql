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
