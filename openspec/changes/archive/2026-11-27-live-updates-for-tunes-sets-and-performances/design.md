## Context

See proposal.md. Observed: `LiveBridge` (`live/internal`) turns module events into NATS messages with
`publishToEveryone` (library subject) or per-account subjects, and `comment.changed` is the model to follow. `track`
already publishes `NewTrackSaved`, `TrackUpdated` and `TrackDeleted` from `track::event` (published through
`TransactionalEvents`, so listeners run after commit). `tune` and `set` publish no events and have no `event`
package. Tunes are created in `TuneService.create`/`importTune` and by `TuneDumpImport`; Sets in `SetService.create`.
Frontend pages load once in `onMount` and do not refetch; `refreshOnLive` exists for that.

## Goals / Non-Goals

**Goals:** the three event types, public and id-only; pages follow them. **Non-Goals:** see Out of scope.

## Decisions

- **New events**: `TuneChanged(String tuneId)` in `tune::event`, `SetChanged(String setId)` in `set::event`, each in a package marked `@NamedInterface("event")`. Published with `TransactionalEvents.publish` after the insert, only when the call created something (not when an import finds an existing Tune). Alternative: one generic event: rejected, modules own their vocabulary and `live` must not depend on internals.
- **Performances reuse track events**: `LiveBridge` listens to `NewTrackSaved`, `TrackUpdated` and `TrackDeleted` and publishes `performance.changed` with the Track id. Unlike `search`/`media` events these are raised inside the writing transaction and delivered after commit, so a browser refetching on the hint sees the change. A metadata-only Track edit also announces it: a harmless extra hint, cheaper than a new event. Alternative: a dedicated `PerformancesChanged` event: rejected, same trigger points and more code.
- **Bulk import**: `TuneDumpImport` inserts thousands of Tunes; per-Tune events would flood NATS and every browser. It publishes a single `TuneChanged(null)` on completion, sent as an event without `id` (`LiveEvent` already allows a missing id; verify it serialises as such).
- **Audience**: all three on the library subject (everyone). Data is public; events stay id-only so reads still go through the API and its authorization.
- **`live` module**: `allowedDependencies` gains `tune::event`, `set::event`, `track::event`; `package-info.java` Javadoc updated. `ModularApplicationTests` guards the boundaries.
- **UI**: `refreshOnLive` in the Tunes list (`tunes/+page.svelte`), Tune page, Sets list and Set page, refetching with the current filters; the Tune page's Performances refetch on `performance.changed`. No new dependency, no loading flash on refetch (keep current content, update in place); a page whose entity no longer exists keeps its not-found state. Burst events are already debounced by `refreshOnLive`.
- **OpenAPI**: no endpoint changes; the `GET /api/live` description lists the new event types.
- **Reactive**: events publish on the bounded elastic scheduler via `TransactionalEvents`; no new blocking call.

## Risks / Trade-offs

- [A bulk import still makes every open Tunes list refetch once] → one event, debounced; acceptable.
- [Extra `performance.changed` on metadata-only Track edits] → harmless refetch.

## Migration Plan

Additive; no data migration. Rollback is reverting the change.
