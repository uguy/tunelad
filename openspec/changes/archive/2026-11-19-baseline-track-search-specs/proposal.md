# Proposal

## Why

The project has no specs and no tests that cover the track REST API: `TrackTest` is empty, and the README makes claims (range streaming, upload) that the code only partly backs. Upcoming fixes (range streaming, upload validation, event wiring, a real search module) need a trustworthy description of current behaviour and a safety net to change it against.

## What Changes

- Record the **current, intended-to-keep** behaviour of the track REST API as baseline specs: upload, list, get by id, play, delete all, and full-text search.
- Add characterization tests (WebTestClient + Testcontainers MongoDB) with one test per spec scenario, proving every baseline requirement against the running application.
- No production code changes. No REST API changes, nothing breaking.
- Known defects are deliberately **not** specified, so the baseline never codifies them (see Out of scope).

## Capabilities

### New Capabilities
- `track`: managing tracks through the REST API - uploading a track with its audio file, listing tracks, fetching one by id, playing its audio, and deleting all tracks.
- `search`: finding tracks by free text through the REST API - word-based, case-insensitive matching on title and description, relevance ordering, and blank-query behaviour.

### Modified Capabilities
- None (no specs exist yet).

## Out of scope

Each item below is a known gap left for its own follow-up change:

- Range requests: `play` currently requires a `Range` header (400 without it) but ignores its value and always streams the full file (`fix-range-streaming`).
- Upload validation: title constraints declared on `AddTrackCommand` are not enforced, and non-mp3 files are stored with format `UNSUPPORTED` instead of being rejected (`validate-track-upload`).
- Domain events: `NewTrackSaved` / `AllTracksDeleted` have no listener, and `AllTracksDeleted` may be published even if deletion fails (`wire-track-events`).
- Tags: the UI renders tags, but the API never exposes them (`track-tags`, or removal).
- Moving search into the empty `search` Modulith module (`extract-search-module`).
- UI behaviour and Playwright e2e tests: the e2e suite is stale and not run by the build.

## Impact

- **Modulith modules**: `track` (all behaviour under test lives here); `search` (spec only - the module is still empty, search is served by `track` at `GET /api/tracks/search`).
- **Surface**: REST API only; no UI change.
- **Code**: new test classes under `src/test/java/org/tunelad/track/` (replacing the empty `TrackTest`), reusing `TestContainerDefinitions` and the mp3 fixtures in `src/test/resources/mp3/`.
- **Specs**: creates `openspec/specs/track/spec.md` and `openspec/specs/search/spec.md` on archive.
- **PR title**: `test(track): add baseline specs and characterization tests for track and search`
