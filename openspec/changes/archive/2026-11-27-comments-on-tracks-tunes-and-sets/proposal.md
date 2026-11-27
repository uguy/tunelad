Issue: #844

## Why

Accounts can listen to Tracks and browse Tunes and Sets, but cannot talk about them. A **Comment** (GLOSSARY) lets
people share what they notice about a recording, a Tune or a Set, which is the point of a collection built to learn
and play sets together.

## What Changes

- New `comment` module: an Account can leave a Comment on a Track, a Tune or a Set; Comments are flat plain text, listed oldest first.
- Reading Comments is open to anonymous visitors; posting needs an Account.
- Only the author or an Administrator edits or deletes a Comment.
- A Comment change is announced as a Live update, so open pages refresh without reload.
- Deleting a Track deletes its Comments. (Sets and Tunes cannot be deleted yet; the same cleanup applies to a Set once it can be.)
- REST API: `GET/POST /api/{tracks|tunes|sets}/{id}/comments`, `PATCH/DELETE /api/comments/{id}`. All additive, nothing breaking.
- UI: a comments section on the Track, Tune and Set detail pages.

## Capabilities

### New Capabilities
- `comment`: posting, reading, editing and deleting Comments on Tracks, Tunes and Sets, with authorisation rules and cleanup.

### Modified Capabilities
- `live`: new `comment.changed` event, public like the library events, and the UI refetching Comments on it.

## Impact

- Modulith modules: new `comment` (depends on `infrastructure`, `account`, `track`, `track::event`, `tune`, `set`); `live` gains a dependency on `comment::event`. Touches REST API and UI.
- Database: new Liquibase changelog `comment.sql`.
- OpenAPI description gains the comment endpoints.
- GLOSSARY.md: `Comment` is refined (flat plain text, edited/deleted by author or Administrator) and `Live update` lists Comments.
- Suggested PR title: `feat(comment): comment on tracks, tunes and sets`.

## Out of scope

- Threads or replies, reactions, mentions, rich text or markdown, attachments.
- Moderation beyond Administrator deletion (reports, bans, rate limits).
- Notifications to the subject's owner.
- Deleting Sets or Tunes themselves (Comment cleanup for a Set is wired when that exists).
- Pagination of Comments.
