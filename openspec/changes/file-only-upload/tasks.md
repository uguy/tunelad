# Tasks

## 1. Branch

- [x] 1.1 Create `feat/file-only-upload` from an up-to-date `main`

## 2. Backend: a Track from a file alone (`track`)

- [x] 2.1 Liquibase changeset in `track.sql`: `provided_fields TEXT[] NOT NULL DEFAULT '{}'`, set to all four fields for existing rows; map it in `Track` and `DbTracks`
- [x] 2.2 `AddTrackCommand`: title, description and `setId` optional; `DefaultTrackService` falls back to the file name (no extension, 80 characters, `Untitled`), builds no Performances without a Set, records the provided fields, and rejects `performances` without `setId`
- [x] 2.3 `TrackController.create`: only `file` required, blank text parts treated as absent, filename passed on (path stripped); update the OpenAPI summary, description and responses
- [x] 2.4 `TrackEdit` and `UpdateTrackCommand`: description optional and cleared when blank, all four fields recorded as provided; update the OpenAPI text and `TrackDTO` description schema
- [x] 2.5 Tests (`TrackApiTest`, `TrackPerformancesTest`): file only, long and blank names, blank parts, missing file, performances without a Set, edit with description emptied, a Set-less Track played, edited and deleted
- [ ] 2.6 Commit `feat(track): upload a track from its audio file alone`

## 3. Backend: tags (`media`, `identification`, `track`)

- [ ] 3.1 `media`: `MediaTags` public interface, `AudioTags` record and ffprobe implementation with `FfprobeProperties`; unavailable, failing or timed-out probe yields empty tags
- [ ] 3.2 `track::command`: `ApplyTags` command and `TrackService.execute(ApplyTags)` under `findByIdForUpdate`, filling only fields not provided, through `Artists.findOrCreate`, publishing `TrackUpdated` only on change
- [ ] 3.3 `identification`: `TagsOnUpload` listener on `NewTrackSaved`, failures logged and never raised
- [ ] 3.4 `TestAudio`: tagged fixtures built with ffmpeg for mp3, m4a, ogg, flac and WMA
- [ ] 3.5 Tests: tag fill per format, partial and blank tags, shared Artist, uploader's values kept, edit before and after, redelivered event, no tags, unreadable audio, ffprobe missing, no analysis request for a Set-less Track, `track.indexed` and search by the tag title
- [ ] 3.6 `ModularApplicationTests` still passes
- [ ] 3.7 Commit `feat(identification): fill a track's blank metadata from the file's tags`

## 4. Frontend

- [ ] 4.1 `Tracks.ts`: optional description, upload payload sends only filled fields, new 400 message and the optional-Set path
- [ ] 4.2 Upload page: file first, "(optional)" labels, hint, "No set yet" default hiding Performance fields, file-missing error; theme tokens only, accessible labels
- [ ] 4.3 `TrackList` and the edit form: no empty description, artist or album lines, no Tunes section without a Set, description optional in the form
- [ ] 4.4 Tests: vitest for the form and list; Playwright e2e with `page.route` for a file-only upload and the list updating when `track.indexed` arrives; update existing e2e that expect a required title
- [ ] 4.5 `bun run format`, lint and check; run `svelte-autofixer` on the changed components
- [ ] 4.6 Commit `feat(ui): upload a track with only its audio file`

## 5. Docs

- [ ] 5.1 `GLOSSARY.md`: Track records a Set only when it has one; Identification also reads the file's tags
- [ ] 5.2 Check `docs/adr` for a decision this contradicts; add an ADR for reading tags in `media` and recording provided fields only if the check says one is needed
- [ ] 5.3 `README.adoc`: features (file-only upload, tags) and ffprobe next to ffmpeg in the requirements
- [ ] 5.4 Commit `docs(track): describe the file-only upload`

## 6. Verify and ship

- [ ] 6.1 `./mvnw clean verify` passes
- [ ] 6.2 Archive the change on the branch (`/openspec-archive-change file-only-upload`), syncing specs
- [ ] 6.3 Push and open the PR titled `feat(track): upload a track from its audio file alone`, body ending `Closes #839`
