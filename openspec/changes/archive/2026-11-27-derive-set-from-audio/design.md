# Design

## Context

- `IdentificationService.identify` filters out a Track without a Set, and `CadenceSource.detect` asks the sidecar for the Tunes of a Set (thesession.org ids in `tunes=`). The sidecar's worker already analyses with `allowed` optional; only `server.ts` refuses a request without `tunes` (400).
- `Identifier.identify` returns every detection with `tuneId` (thesession id), `name`, `start`, `end`, `confidence`. Restricted Identification maps thesession ids to the Set's Tunes; open-ended needs the reverse: thesession id to a catalogue Tune, importing it when absent.
- `TuneCatalog` has only `findAllById`. `TuneService.importTune(AccountRef, int)` finds or fetches and inserts. `SetService.create(AccountRef, List<String>)` finds or creates a Set; `SetCatalog` has only reads. `identification` may already depend on `tune`, `track`, `track::command`, `media`, `account`; not on `set`.
- A Track's Set is stored in `track_tracks.set_id` by `DbTracks.storeSetId`, and Performances in `track_performances`; `ProposePerformances` shows the row-lock pattern (`findByIdForUpdate`, write only what changed, publish `TrackUpdated`). Slice 1 made a Track without a Set legal.

## Goals / Non-Goals

**Goals:**
- A Set-less Track gets a Set and identified Performances after upload without anyone naming the Tunes.
- A Set an Account chose is never replaced, whatever the timing.

**Non-Goals:**
- Tuning the recognition engine, or changing how restricted Identification works.
- A UI for correcting a derived Set (a Set can be changed by no one yet, see proposal).

## Decisions

**1. The open-ended request is the same endpoint without `tunes`.** `POST /identify` with no `tunes` reports every detection; with `tunes` it behaves as today. `IdentificationSource` gains `detectAny(Flux<DataBuffer>)`, implemented in `CadenceSource` with the same streaming, timeout and ordering. The sidecar test and README are updated; the 400 for a missing body stays.
- *Alternative: a second endpoint (`/recognise`).* Rejected: same input and output, one more route to document.

**2. The decision is made in `identification`, in a pure function.** `SetDerivation.derive(List<DetectedTune>, double minConfidence)` returns the ordered list of thesession ids with their start (confidence filter, consecutive same-Tune merge keeping the first start, repeats after another Tune kept, first twenty). It is unit-tested without Spring, like `ProposalsTest`. `tunelad.identification.min-confidence` (default 0.5) lives in `IdentificationProperties`.
- *Assumption:* the engine's confidence of 0.60 in the sidecar's example is a typical true detection, so 0.5 is a starting point; it is a property so it can be tuned without code.

**3. Tunes are resolved by thesession id, importing the missing ones.** `TuneCatalog` gains `Mono<TuneDTO> findOrImport(AccountRef owner, int sourceId)`: the catalogue's Tune with that `sourceId`, else imported like `importTune`, else empty when thesession.org has no such Tune or cannot be reached (the error is logged, not raised). Entries are resolved one at a time, in order, so imports are not hammering thesession.org and the order is kept; an entry that resolves to nothing is dropped, and the remaining entries still make the list. The imported Tunes are owned by the Track's Owner, who is the Account on whose behalf the upload happened (`TrackDTO.owner` gives id and display name; an `AccountRef` is built from it as `ProposePerformances`' callers already know the id).
- *Alternative: skip unknown Tunes.* Rejected with the user: a fresh install would almost never derive a Set.
- *Alternative: import by the sidecar's `name`.* Rejected: ids are the engine's currency and the catalogue already keys on them.

**4. The Set is found or created through `SetCatalog`.** `SetCatalog` gains `Mono<SetDTO> findOrCreate(AccountRef creator, List<String> tuneIds)` (delegating to `SetService.create`, whose 1 to 20 Tunes rule matches the cap). The creator is the Track's Owner. `identification`'s `allowedDependencies` gains `set`.

**5. Attaching is a track command under the row lock.** `AttachSet(trackId, setId, startOffsets)` in `track::command`, handled by `TrackService.execute(AttachSet)` in a `@ReactiveTransactional` method: `findByIdForUpdate`; if the Track is gone or already has a Set, complete empty (the result is discarded); else load the Set through `SetCatalog`, set `setId` and `tuneSet`, build one Performance per Tune with default key/notes, then mark each `proposed(start)` (so `IDENTIFIED`), save the Set id and the Performances (`DbTracks.save` already writes both), and publish `TrackUpdated`. A Set whose size differs from the offsets is a programming error and fails loudly.
- No new column: `set_id` and the Performance tables already exist.

**6. `identify` branches on the Set.** `IdentificationService.identify(trackId)`: Track gone, or has a Set with Performances: existing restricted path; no Set: `deriveSet(track)`; analysis failure is raised as today (the listener and controller log it, the Track is untouched). Both triggers (the upload listener and `POST .../identification`) call `identify`, so the re-run needs no controller change beyond its OpenAPI text. No Set but nothing derived: complete empty.

**7. Concurrency.** Two analyses of one Track may race (upload and a quick re-run): the row lock plus "only when still Set-less" in decision 5 means the first wins and the second is a no-op. An Account's edit cannot give a Set (no such API), so the "Set given while the analysis ran" case in the spec is covered by this race and by a direct service test.

**8. UI.** No new components. `TrackList` already refetches an open Track's details on `track.indexed` (`refreshDetail`); the spec adds a test that the Tunes section appears when the refetched Track has a Set. If that test shows the details are not refreshed for a Track that had none, the fix is in the same task group.

**9. Cost and limits.** The open-ended pass queries the whole index; the sidecar README says about 5 % of the audio's duration for restricted mode and the open-ended figure is measured in the sidecar test and written there. `tunelad.identification.timeout` (default 10 min) is unchanged; a timeout is a failure that leaves the Track as it was.

## Risks / Trade-offs

- [False positives from an unrestricted search create wrong Sets and import wrong Tunes] → confidence floor, nothing is derived when nothing is confident, and the Performances read `identified` so the UI shows they are machine proposals; a wrong Set cannot yet be changed by hand (out of scope, flagged for a follow-up).
- [thesession.org down or slow] → each import failure only drops that entry; the existing 502/timeouts behaviour of the importer is not reached because the call is internal and its errors are caught.
- [Imported Tunes are owned by the uploader] → consistent with a manual import by that Account; they are shared catalogue entries like any other.
- [A long recording hears more than twenty Tunes] → the first twenty only, stated in the spec.
- [Re-run on a Track whose first analysis wrongly derived a Set] → it is analysed against that Set as today and does not change the Set.

## Migration Plan

1. Deploy the sidecar first (a request without `tunes` is an error on the old sidecar, which the backend treats as a failed analysis, so the order is safe either way).
2. Deploy the backend; no schema change.
3. Rollback: revert; Sets derived in the meantime stay valid Sets.

## Open Questions

- The right default confidence floor is unknown until it is tried on real recordings; 0.5 is a placeholder behind a property.
