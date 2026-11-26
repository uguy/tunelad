--liquibase formatted sql

--changeset tunelad:search-tracks
-- The search read model, filled from track events: its id is the track's id, with no foreign key to the track module.
-- Title words weigh more than description words.
CREATE TABLE search_tracks
(
    id                 TEXT NOT NULL,
    title              TEXT,
    description        TEXT,
    artist             TEXT,
    album              TEXT,
    format             TEXT,
    owner_id           TEXT,
    owner_display_name TEXT,
    search_vector      TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(description, '')), 'B')) STORED,
    PRIMARY KEY (id)
);
CREATE INDEX search_tracks_search_vector_idx ON search_tracks USING gin (search_vector);
