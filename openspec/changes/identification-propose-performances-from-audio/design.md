## Context

`track` stores Performances (`track_performances`: offset, key, notes, abc) and publishes `NewTrackSaved(TrackDTO)` in `track::event`. Nothing analyses audio.
ADR 0001 notes FolkFriend and irishtune.info coverage, licences and band-recording quality are unverified.

## Goals / Non-Goals

**Goals:** background, non-blocking proposals; states `pending`/`identified`/`corrected`; corrections always win; a source can be swapped or fail without harm.
**Non-Goals:** hosting models, proposing Sets, live progress (#824).

## Decisions

- **Research first.** Task group 1 produces `docs/research/identification-sources.md` (APIs, auth, licence, limits, band-recording results) and, if needed, amends ADR 0001 and this design. Nothing else starts before it. If neither source is usable, stop and revisit the slice.
- **Module `identification`**, `allowedDependencies = { "infrastructure", "track::event", "track::command", "media", "tune" }`. It listens to `NewTrackSaved` (via `@ApplicationModuleListener`, asynchronous, after commit) and applies proposals through a `track::command` API; it never touches `track.internal`. `track` must not depend on `identification`, so the rerun endpoint lives in `identification.web`.
- **Port and adapters.** `IdentificationSource` returns a Flux/Mono of `PerformanceProposal(position, startOffsetSeconds, key, abc)`. One adapter per source (`FolkFriendSource`, `IrishTuneSource`), built on `WebClient` with timeouts; results merged, FolkFriend preferred for offsets and irishtune.info for matching a Tune.
- **State in `track`.** New column `identification TEXT NOT NULL` on `track_performances` (`pending|identified|corrected`), default `corrected` for existing rows, `pending` for new ones. A track command `ProposePerformances` updates only rows not `corrected`, in one transaction, so the rule is enforced in one place. `UpdateTrackCommand` sets `corrected` for rows whose values changed.
- **Audio access.** Read the stored audio through the `media` public API as a `Flux<DataBuffer>` streamed to the source; no blocking file reads. Any CPU-heavy decoding is isolated on `Schedulers.boundedElastic()`.
- **Failures.** Errors are logged and swallowed per source; states stay unchanged; the re-run endpoint is the retry. Idempotent, since proposals are keyed by Performance position.
- **REST / OpenAPI.** `PerformanceDTO` gains `identification` (enum schema `IdentificationState`); `POST /api/tracks/{id}/identification` documented as 202/403/404. Additive, not breaking.
- **UI.** A small state badge (theme tokens, text not colour alone, accessible name) per Performance on the tune view and a "Run identification again" button for owners; refetch through `refreshOnLive`.

## Risks / Trade-offs

- Sources may be unusable, rate-limited or licence-restricted → research gate; port keeps the choice open.
- Proposals can be wrong → they are only proposals; any edit makes them `corrected`.
- Async work lost on restart → Spring Modulith event publication registry replays incomplete listeners.

## Migration Plan

Liquibase changeset adds the column (existing rows `corrected`, so nothing changes for old data); rollback drops it.

## Open Questions

- Which source answers which field, per the research outcome?
- Should Identification propose the Set for Tracks without one (out of scope here)?
