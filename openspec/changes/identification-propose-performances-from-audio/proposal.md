Issue: #823

# Proposal

## Why

Uploading a Track means typing in its Set and, for every Performance, its start offset, key and Transcription. Most of that can be proposed from the audio.
**Identification** (see `GLOSSARY.md`) analyses a Track's audio in the background after upload and proposes its Performances and Transcriptions from
FolkFriend and irishtune.info, so an Account only checks and corrects. The roadmap (#815) needs it now that Performances own the Transcription (ADR 0001).

## What Changes

- New `identification` module: after a Track is saved, it runs an analysis in the background and proposes values for each of the Track's Performances.
- Each Performance gets an Identification state: `pending` (analysis not finished), `identified` (proposed by the machine), `corrected` (edited by an Account).
- A proposal fills only what is still empty or `pending`/`identified`; a Performance that is `corrected` is never overwritten, including by a re-run.
- Editing a Performance by hand (offset, key, notes, Transcription) marks it `corrected`.
- `PerformanceDTO` gains `identification` (the state) and the track API shows it; the track view shows the state and lets an Account re-run Identification.
- `POST /api/tracks/{id}/identification` (owner or administrator) re-runs the analysis.
- **First task is a research ticket**: the FolkFriend and irishtune.info APIs, licences, rate limits and whether FolkFriend copes with band recordings are
  unverified (ADR 0001). Findings are recorded under `docs/` and decide the adapter; the rest of the design assumes a pluggable source behind one port.
- Assumption (to confirm): a Track is still uploaded with its Set. Identification proposes offset, key and Transcription for the Performances the Set already
  gives; proposing the Set itself from audio is out of scope.

REST change is additive, not breaking (new response field, new endpoint). UI and API both touched.

## Capabilities

### New Capabilities
- `identification`: background analysis of a Track's audio after upload, the state of each Performance, proposals from external sources, and never overwriting manual corrections.

### Modified Capabilities
- `track`: a Performance exposes its Identification state, and editing a Performance by hand marks it `corrected`.

## Impact

- Modules: `identification` (new, depends on `track::event`, `track::command`, `media`, `infrastructure`), `track` (state column, DTO, edit rule). `package-info.java` `allowedDependencies` updated for both.
- Database: Liquibase change adding the Identification state to `track_performances`.
- OpenAPI: `PerformanceDTO.identification`, new `POST /api/tracks/{id}/identification`; README screenshots retaken if the UI changes.
- Frontend: state badge and re-run button on the tune/track view; Playwright e2e with `page.route` mocks.
- Dependencies: a reactive `WebClient` to the external sources (no new library expected); no secrets until research says otherwise.
- `GLOSSARY.md`: **Identification** states are already defined; check wording after the research outcome.

## Out of scope

- Training or hosting models.
- Proposing the Set (which Tunes a Track contains) from audio.
- Importing Tunes from the sources beyond matching to existing Tunes (Tune identity stays the thesession.org id, ADR 0001).
- Bulk re-identification of Tracks stored before this change.
- Live updates of Identification progress (slice #824).

## Suggested PR title

`feat(identification): propose performances from audio`
