--liquibase formatted sql

--changeset tunelad:comment-comments
-- subject_id and author_id refer to the track, tune, set and account modules by value: no foreign key across modules.
-- clock_timestamp() rather than now(), so that two comments in one transaction still have an order.
CREATE TABLE comment_comments
(
    id           TEXT                     NOT NULL PRIMARY KEY,
    subject_type TEXT                     NOT NULL,
    subject_id   TEXT                     NOT NULL,
    author_id    TEXT                     NOT NULL,
    author_name  TEXT,
    text         TEXT                     NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT clock_timestamp(),
    edited_at    TIMESTAMP WITH TIME ZONE
);
CREATE INDEX comment_comments_subject_idx ON comment_comments (subject_type, subject_id, created_at);
