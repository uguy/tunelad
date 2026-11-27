## Context

`track` owns the recording (audio via `media`, metadata, owner); `set` owns Sets of Tunes; `tune` owns Tunes. A Performance is "one Tune as played in one Track"
(ADR 0001), so it belongs to `track` and refers to the Tune by id, like `set_set_tunes` does. Existing Tracks have no Set. Other-module scenarios in the
`track` spec that upload "with a title and description" (formats, size limit, aborted upload) now implicitly carry a valid `setId`; their behaviour is unchanged.

## Decisions

- **Module dependencies**: `track`'s `package-info.java` allows `set` and `tune` (names and Dance types arrive inside `SetDTO`; `DanceType` is in `tune`'s root package). `set` publishes
  `org.tunelad.set.SetCatalog` (`Mono<SetDTO> findById(String)`, empty when unknown), implemented by `SetService`, like `TuneCatalog`. `SetDTO.Entry` already carries
  the Tune id, name and Dance type, so Performances read these through the Set and no copy of a Tune name is stored (renames show at once). No cycle: `set` does not depend on `track`.
- **Schema** (`track.sql`, new changeset): `track_tracks.set_id TEXT NULL` (a Set of the `set` module, by value, no cross-module foreign key; null for Tracks stored before) and
  `track_performances (track_id TEXT REFERENCES track_tracks ON DELETE CASCADE, position INT, tune_id TEXT, start_offset_seconds NUMERIC(9,3) NOT NULL DEFAULT 0, key TEXT, notes TEXT, PRIMARY KEY (track_id, position))`
  with an index on `tune_id` (Tune page, #820). Existing rows are not migrated: no Set, no Performances. Rollback drops the table and column. Add both to the truncate in `AbstractApiTest`; `ModuleTablesTest` must still pass.
- **Where Performances live in code**: a `Performance` row type and a `Performances` repository (`DatabaseClient`, as `Sets`) in `track.internal`; `Track` stays the aggregate for
  its columns, with `setId`. Performances are written in the same `@ReactiveTransactional` as the Track row, after the audio is stored. Position is 1-based in the API, stored as 1-based.
- **Upload**: `TrackController.create` gains `@RequestPart("setId")` and optional `@RequestPart("performances") String`, parsed with Jackson into a record list (`PerformanceInput(startOffsetSeconds, key, notes)`);
  a non-array, a bad number or a wrong count is a 400 (`BadRequestException`). A string part is used rather than a JSON-typed part because browsers' `FormData` send JSON as text unless wrapped in a `Blob`.
  `AddTrackCommand` gains `setId` and `List<PerformanceInput> performances`. The service resolves the Set through `SetCatalog` *before* touching the audio (unknown Set: 400, nothing stored), builds one Performance per Set entry, then saves as today.
  "A failed upload leaves nothing behind" is unaffected: validation happens before audio is written, and the event `NewTrackSaved` fires after commit as before.
- **Validation** lives in one place (`PerformanceInput` factory/validator in `track.command`): offset ≥ 0 and ≤ 86,400, key ≤ 30 characters, notes ≤ 2000, blank key/notes stored as null.
- **Edit**: `TrackEdit` gains optional `performances`; `UpdateTrackCommand` gains the same list. The count must equal the Track's Performances (400 otherwise); only offset/key/notes are updated by position.
  The owner/administrator check is the existing one, before any change. A Track without a Set accepts an omitted or empty list only.
- **Read**: `TrackDTO` gains `SetDTO set` (null for legacy) and `List<PerformanceDTO> performances` (a new record in `org.tunelad.track`: `position, tuneId, tuneName, danceType, startOffsetSeconds, key, notes`).
  `TrackMapper` assembles it from the Performance rows and the Set read through `SetCatalog`. Lists load Performances and Sets in batched queries (one per page of Tracks) to avoid N+1.
  `SetCatalog` therefore also offers `findAllById(Collection<String>)`.
- **Events and delete**: `TrackDeleted` handlers in `favourite` and `playlist` are unchanged; Performances go by `ON DELETE CASCADE`. `NewTrackSaved`/`TrackUpdated` carry the larger `TrackDTO`; search ignores the new fields. No new events (Live updates for Sets and Performances are #824).
- **Reactive**: nothing blocks; JSON parsing of `performances` is a small in-memory step.
- **OpenAPI**: `POST /api/tracks` documents the new `setId` and `performances` parts and the new 400 causes (marked breaking in the description); `TrackDTO` gains `set` and
  `performances` schemas; `PUT /api/tracks/{id}` documents `performances`. `doc/api-screenshots.mjs` screenshots are retaken if the Scalar view changes visibly.
- **UI**: the Set builder of `sets/new` is extracted to a shared component (`SetBuilder.svelte`, tune search, add, up/down, remove, `aria-live`) reused by the upload page. The upload page offers a
  select of existing Sets (named by Tunes, from `GET /api/sets`) or "Build a new set" that reveals the builder; per-Tune Performance fields (start as `m:ss` text converted to seconds, key, notes)
  are one `fieldset` with a `legend` per Tune. A new Set is created with `POST /api/sets` just before the upload; if the upload then fails the Set remains (Sets are harmless and shared). Track page shows Performances as an ordered list.
  Theme tokens only, labelled controls, visible errors, focus moved to the first invalid field.

## Risks / Trade-offs

- Breaking upload API: any client that sends no `setId` gets 400. Acceptable for a pet project; flagged in the PR title with `!`.
- Legacy Tracks cannot gain a Set in this slice, so the library is split between old and new Tracks until Identification or a later slice.
- Creating the Set before the upload can leave an unused Set after a failed upload; accepted, as a Set may exist without Tracks by definition.
- A `performances` string part is less self-describing than a JSON part, but works with plain `FormData`.
