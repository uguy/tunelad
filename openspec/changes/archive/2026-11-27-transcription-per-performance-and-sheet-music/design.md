# Design

## Context

`track_performances` (track.sql) stores position, tune_id, start offset, key and notes; `PerformanceInput` validates key and notes and `TrackEdit` carries a list of them. The Tune page (`app/src/routes/tunes/[id]/+page.svelte`) renders `tune.abc` with `AbcViewer`, which lazy-loads abcjs. Module rule: `track` may depend on `tune`, not the reverse.

## Goals / Non-Goals

**Goals:** store and edit a Transcription per Performance; draw it on the Tune view with fallback.
**Non-Goals:** ABC validation or transposition; changes to `AbcViewer`.

## Decisions

- **List endpoint lives in `track`** (`GET /api/tunes/{id}/performances`, a second controller mapped under `api/tunes`). The Tune view needs Performances, which `tune` cannot read without a cycle. Alternative: embed Performances in `TuneDTO`, rejected as it needs a `tune → track` dependency or an event-built projection, too heavy here.
- **Reactive query**: a `DatabaseClient` / repository query joining `track_performances` with `track_tracks` and `Artist`, returning a `Flux`; no blocking calls. Exposed through a new `TrackService.findPerformancesByTune(String tuneId)` returning a public `TunePerformanceDTO`.
- **Column**: nullable `abc TEXT` added by a new Liquibase changeset with a rollback; existing rows stay null. Limit 20000 chars enforced in `PerformanceInput.validate()` like key and notes, with a matching client check in `Performances.ts`.
- **OpenAPI**: `abc` added to the `PerformanceDTO` and `PerformanceInput` schemas, new path `/api/tunes/{id}/performances` with `TunePerformanceDTO`, via springdoc annotations.
- **UI**: the Tune page fetches performances after the Tune (`Tunes.ts`), renders one `AbcViewer` per Performance inside a titled section, else the Reference transcription. Heading levels and text labels keep it accessible; abcjs stays lazy and shared.
- **Staleness**: no live refresh now (#824).

## Risks / Trade-offs

- Many Performances → many abcjs renders on one page → list is ordered and each viewer is cheap; revisit if a Tune has dozens.
- Malformed user ABC may render oddly → `AbcViewer` already offers the raw ABC view; no validation added.

## Migration Plan

Liquibase runs at startup; additive column, rollback drops it.
