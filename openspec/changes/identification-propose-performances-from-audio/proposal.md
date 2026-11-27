Issue: #823

# Proposal

## Why

Uploading a Track means typing in its Set and, for every Performance, where its Tune starts. That can be proposed from the audio.
**Identification** (see `GLOSSARY.md`) analyses a Track's audio in the background after upload and proposes its Performances, so an Account only checks and
corrects. The roadmap (#815) needs it now that Performances are recorded per Track (ADR 0001).

## What Changes

- New `identification` module: after a Track is saved, it sends the audio and the Tune ids of the Track's Set to a **sidecar** and proposes a start offset for each Performance whose Tune is detected.
- The sidecar is a Node container running Cadence's recognition engine (FolkFriend WASM, note decoder, Viterbi segmenter; 10 s windows, 5 s hop) behind a small HTTP wrapper. The Java module calls it through the `IdentificationSource` port, so the engine can later be replaced (for example by a Java port).
- Each Performance gets an Identification state: `pending` (not analysed yet, or the analysis could not be done), `identified` (proposed by the machine), `nothing_found` (an analysis finished without hearing its Tune) or `corrected` (edited by an Account).
- A proposal fills only Performances that are `pending` or `identified`; a `corrected` Performance is never overwritten, including by a re-run.
- Editing a Performance by hand (offset, key, notes, Transcription) marks it `corrected`.
- `PerformanceDTO` gains `identification`; the track view shows the state and lets an Account re-run Identification.
- `POST /api/tracks/{id}/identification` (owner or administrator) re-runs the analysis.
- Research is done (`docs/research/identification-sources.md`). The bare FolkFriend CLI failed on a real duo recording; Cadence's pipeline found "The Mossy Banks" in every window from 5 s to 105 s (scores 0.47 to 0.76). irishtune.info has no API or licence and is dropped.

Assumptions (confirmed with the owner):
- A Track is still uploaded with its Set. Only Tunes of that Set are accepted; a detection of any other Tune is ignored, which also limits false positives.
- This slice proposes **start offsets only**. Key and Transcription proposals are a later slice (the engine returns Tune and time, not a key or ABC); #823 is to say so.
- Start offsets are accurate to about 2.5 s (half a hop); they stay editable.
- Tunes missing from the thesession.org index (the second reel of the test recording seems to be one) can never be identified: once an analysis has looked for them, their Performances say `nothing_found`. A Tune with no thesession.org id (made by hand) is never looked for, so its Performances stay `pending`.
- The sidecar is AGPL-3.0 / GPL-3.0 code (Cadence and its vendored FolkFriend), accepted as a separate container whose source stays public in this repository with its own `LICENSE` and a notice.

REST change is additive, not breaking (new response field, new endpoint). UI and API both touched.

## Capabilities

### New Capabilities
- `identification`: background analysis of a Track's audio after upload, the state of each Performance, proposals from external sources, and never overwriting manual corrections.

### Modified Capabilities
- `track`: a Performance exposes its Identification state, and editing a Performance by hand marks it `corrected`.

## Impact

- Modules: `identification` (new, depends on `track::event`, `track::command`, `media`, `infrastructure`), `track` (state column, DTO, edit rule). `package-info.java` `allowedDependencies` updated for both.
- New sidecar: a `sidecar/identification/` directory (Node, vendored engine with its licence), a service and an index volume in `docker-compose.yml`, and CI building its image.
- Database: Liquibase change adding the Identification state to `track_performances`.
- OpenAPI: `PerformanceDTO.identification`, new `POST /api/tracks/{id}/identification`; README screenshots retaken if the UI changes.
- Frontend: state badge and re-run button on the tune/track view; Playwright e2e with `page.route` mocks.
- Dependencies: a reactive `WebClient` to the sidecar (no new Java library); the sidecar downloads the thesession.org index once and caches it on a volume.
- `GLOSSARY.md`: **Identification** says it proposes Performances and Transcriptions; narrow the wording to what this slice does. ADR 0001 consequences updated.

## Out of scope

- Training or hosting models.
- Proposing the Set (which Tunes a Track contains) from audio.
- Proposing key or Transcription (a later slice).
- Using irishtune.info, and a Java port of the engine.
- Refreshing the sidecar's index beyond a volume cache.
- Bulk re-identification of Tracks stored before this change.
- Live updates of Identification progress (slice #824).

## Suggested PR title

`feat(identification): propose performances from audio`
