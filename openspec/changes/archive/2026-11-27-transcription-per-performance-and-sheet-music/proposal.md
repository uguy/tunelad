Issue: #820

## Why

A Performance is where an interpretation is kept (ADR 0001), but today it holds only a start offset, a key and notes: a recording's Transcription cannot be written down, and the Tune detail view shows only the Reference transcription. Each recording of a Tune differs, so the sheet music should follow the recording.

## What Changes

- A **Performance** gains an optional **Transcription** (ABC notation), edited by hand with the other Performance fields on the Track edit form; at most 20000 characters.
- `PerformanceDTO` and `PerformanceInput` carry `abc`; the Track edit (`PUT /api/tracks/{id}`) replaces it like key and notes.
- New read endpoint `GET /api/tunes/{id}/performances`, served by the track module, lists every Performance of a Tune (Track, Artist, position, key, notes, Transcription).
- The Tune detail view draws a sheet for each Performance that has a Transcription, labelled with its Track, and falls back to the **Reference transcription** when no Performance has one.
- Liquibase changeset adds a nullable `abc` column to `track_performances`.
- No **BREAKING** change: the new fields are optional and additive.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `track`: a Performance carries a Transcription, editable on the Track; Performances of a Tune are listed.
- `tune`: the Tune detail view shows each Performance's sheet, falling back to the Reference transcription.

## Impact

- Modules: `track` (entity, DTO, input, controller, changeset), `tune` UI only (no new Java dependency: `track` already depends on `tune`, never the reverse, hence the endpoint lives in `track`).
- REST API and UI both change; OpenAPI gains `abc` on two schemas and one path.
- Frontend: `app/src/routes/tracks/PerformanceFields.svelte`, `Performances.ts`, `app/src/routes/tunes/[id]/+page.svelte`, `Tunes.ts`; abcjs is already lazy-loaded by `AbcViewer`.
- `GLOSSARY.md`: Transcription and Reference transcription already describe this; checked for wording only.

## Out of scope

- Automatic transcription (Identification, a later slice).
- Owner/Administrator permission rules on Performances (#821); existing Track edit rules apply unchanged.
- Live updates for Performances (#824).
- Editing a Tune's Reference transcription.

## PR title

`feat(tune): show transcriptions on the tune view`
