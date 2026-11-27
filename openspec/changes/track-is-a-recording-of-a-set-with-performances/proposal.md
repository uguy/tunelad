Issue: #819

## Why

A **Track** is a recording of a **Set** and holds one **Performance** per Tune of that Set (roadmap #815). Tunes (#817) and Sets (#818) exist, but a Track is
still a bare recording with an artist name. This slice connects them, so that later slices can add Transcriptions, Identification and the Learning list.

## What Changes

- **BREAKING** uploading a Track now requires a Set: `POST /api/tracks` takes a `setId` part. The upload form lets the Account choose an existing Set or build one
  (pick Tunes, order them) before submitting.
- Each Tune of the Set gets a **Performance** in the Track: its order, start offset in the audio, key and notes. The upload accepts them in an optional
  `performances` part (one entry per Tune, in Set order); a missing entry defaults to offset 0, no key, no notes.
- A Track in the API shows its Set and its Performances (with the Tune's name and Dance type), on the list, the detail and the Track page.
- The Owner or an Administrator can edit a Performance's start offset, key and notes with the Track's `PUT`; the Set of a Track does not change.
- Deleting a Track deletes its Performances, and still removes it from every Playlist and Favourite.
- Existing Tracks are kept as they are: they have no Set and no Performance (the default). They stay playable, listed and editable.
- The `set` module publishes a small API (`SetCatalog`) so `track` can check a Set exists and read its Tunes without touching `set.internal`.
- REST: `POST /api/tracks` breaking, `TrackDTO` gains `set` and `performances`, `PUT /api/tracks/{id}` gains optional `performances`; all described in OpenAPI.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `track`: upload requires a Set and carries Performances; Tracks show their Set and Performances; Performances are editable and deleted with their Track; the upload form picks or builds a Set.

## Impact

- Modules: `track` (now allowed to depend on `set` and `tune`), `set` (new named API `SetCatalog`), `tune` (read only through `TuneCatalog`, via `SetDTO`). REST API and UI both change.
- Backend: `track.sql` changelog (`track_performances` table, nullable `set_id` on `track_tracks`), `Track`/`TrackDTO`/commands, `TrackController`, `package-info.java`
  files, table truncate in `AbstractApiTest`.
- Frontend: upload page (Set picker/builder, per-Tune Performance fields), track list and detail show the Set, edit form for Performances; Playwright and vitest tests.
- Docs: `GLOSSARY.md` (checked, see tasks), `README.adoc` features, `openspec/specs/track`.
- Suggested PR title: `feat(track)!: record tracks as sets with performances`

## Out of scope

- Transcriptions, Reference transcription display and music sheets (#820).
- Identification of Performances from the audio (#823).
- Migrating existing Tracks beyond the default of no Set; assigning a Set to an existing Track.
- Changing the Set of a Track after upload, adding or removing Performances.
- Owner and Administrator rules specific to Performances (#821): the existing Track rule applies.
- Learning list (#822) and Live updates for sets and performances (#824).
- Showing Performances in search results.
