## Context

See proposal.md. Observed: modules `track`, `tune`, `set` each expose a public catalog or command API; `account`
exposes `CurrentAccount`/`AccountRef` (with `admin` from the token). Track deletion publishes `TrackDeleted`;
`favourite` already cleans up on it and `live` bridges module events to NATS. Sets and Tunes have no delete path today.

## Goals / Non-Goals

**Goals:** one reusable Comment model for three subject kinds; idiomatic reactive Modulith module.
**Non-Goals:** anything in proposal.md's Out of scope.

## Decisions

- **One `comment` module, one `comment` table** with `subject_type` (`TRACK|TUNE|SET`), `subject_id`, `author_id`, `text`, `created_at`, `edited_at`; index on `(subject_type, subject_id, created_at)`. Alternative: a table per subject: rejected, three copies of identical logic. Subject ids carry no foreign key because subjects live in other modules (same approach as `favourite`).
- **Subject existence check** through each module's public API (`track`, `tune::TuneCatalog`, `set::SetCatalog`), behind one small `Subjects` port in `comment/internal`. If `track` has no public lookup, add a minimal one in its public package and list it in `allowedDependencies`.
- **Authorisation**: author id equals the current `AccountRef.id()`, or `AccountRef.admin()`. Done in the service, not the controller.
- **Cleanup**: a `@ApplicationModuleListener` on `TrackDeleted` deletes the Comments and publishes `CommentChanged(subjectId)`, idempotent like `FavouriteCleanup`. Blocking `.block()` there follows the existing pattern, on the task executor, never on an event loop thread. A Set listener is not added: no `SetDeleted` event exists (out of scope).
- **Live**: `CommentChanged(subjectType, subjectId)` in `comment::event`; `LiveBridge` publishes `comment.changed` on the library subject (everyone). `live` adds `comment::event` to `allowedDependencies`. Events stay id-only.
- **REST**: nested `GET/POST` under each subject, flat `PATCH/DELETE /api/comments/{id}`. OpenAPI: new `comment` tag, five operations, a `CommentDTO` and `CommentInput` schema, 400/401/403/404 documented. Anonymous GET is permitted in the security config alongside the other public reads.
- **Plain text**: validated server side (trim, non-blank, max 2000), rendered by Svelte text interpolation, never `{@html}`.
- **UI**: one `Comments.svelte` component taking `subjectType` and `subjectId`, using `refreshOnLive` for `comment.changed`; theme tokens only, labelled form, buttons with accessible names, `aria-live` polite on the list. Kept thin (no new dependency).

## Risks / Trade-offs

- [Comments of a deleted Set/Tune would linger once those can be deleted] → documented; the Set/Tune deletion slice must add the listener.
- [Orphan comment if a Track is deleted between existence check and insert] → harmless, the `TrackDeleted` cleanup is idempotent and a re-run of it is not needed for rows inserted after; accepted for a pet project.
- [Unbounded list] → pagination deferred (Out of scope).

## Migration Plan

Additive Liquibase changeset creating `comment`; rollback is dropping the table.
