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

--changeset tunelad:search-albums-and-catalogue-tracks
-- Albums are searchable by title and Artist; a Catalogue Track is a search track flagged `catalogue` with its Album.
ALTER TABLE search_tracks
    ADD COLUMN catalogue      BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN album_id       TEXT,
    ADD COLUMN album_position INT;

CREATE TABLE search_albums
(
    id            TEXT NOT NULL,
    title         TEXT NOT NULL,
    artist        TEXT,
    cover_url     TEXT,
    release_date  DATE,
    search_vector TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(artist, '')), 'B')) STORED,
    PRIMARY KEY (id)
);
CREATE INDEX search_albums_search_vector_idx ON search_albums USING gin (search_vector);
--rollback DROP TABLE search_albums;
--rollback ALTER TABLE search_tracks DROP COLUMN album_position, DROP COLUMN album_id, DROP COLUMN catalogue;
