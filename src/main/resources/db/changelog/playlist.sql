--liquibase formatted sql

--changeset tunelad:playlist-playlists
-- owner_id refers to an account of the account module by value: no foreign key across modules
CREATE TABLE playlist_playlists
(
    id          TEXT                     NOT NULL DEFAULT gen_random_uuid()::text,
    owner_id    TEXT                     NOT NULL,
    name        TEXT                     NOT NULL,
    description TEXT,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT clock_timestamp(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (id)
);
CREATE INDEX playlist_playlists_owner_idx ON playlist_playlists (owner_id, updated_at DESC);

--changeset tunelad:playlist-items
-- A track is in a playlist at most once, at one position. The foreign key stays inside the module's prefix;
-- track_id refers to the track module by value. The position is unique but deferred, so that renumbering the items
-- in one transaction never trips over itself half way.
CREATE TABLE playlist_items
(
    playlist_id TEXT NOT NULL REFERENCES playlist_playlists (id) ON DELETE CASCADE,
    track_id    TEXT NOT NULL,
    position    INT  NOT NULL,
    PRIMARY KEY (playlist_id, track_id),
    CONSTRAINT playlist_items_position UNIQUE (playlist_id, position) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX playlist_items_track_idx ON playlist_items (track_id);
