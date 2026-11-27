--liquibase formatted sql

--changeset tunelad:favourite-favourites
-- account_id and track_id refer to the account and track modules by value: no foreign key across modules.
-- clock_timestamp() rather than now(), so that two marks in one transaction still have an order.
CREATE TABLE favourite_favourites
(
    account_id TEXT                     NOT NULL,
    track_id   TEXT                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (account_id, track_id)
);
CREATE INDEX favourite_favourites_track_idx ON favourite_favourites (track_id);
