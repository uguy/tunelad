## 1. Branch

- [x] 1.1 Create `feat/albums-catalogue-tracks` from an up-to-date `main` (already created; verify it is based on `main`)

## 2. Schema and Album basics (commit: `feat(track): add albums and catalogue tracks`)

- [x] 2.1 Liquibase changeset in `track.sql`: `track_albums`, `track_tracks.album_id/album_position`, nullable audio, nullable `track_performances.tune_id` + `listed_name`, with rollbacks
- [x] 2.2 Album entity, repository, service, `POST/GET /api/albums`, `GET /api/albums/{id}`; `AlbumChanged` event in `track::event`
- [x] 2.3 Catalogue Track in `Track`/`TrackDTO` (`catalogue`, `albumId`, `albumPosition`), no audio stream (404), unresolved Performance (`listedName`)
- [x] 2.4 Tests (Testcontainers): create Album, blank title, fetch Catalogue Track, audio 404, unresolved Performance, Set only when all resolve
- [x] 2.5 Update OpenAPI annotations for the new operations and schema fields

## 3. Recordings import (commit: `feat(track): import albums from thesession.org recordings`)

- [x] 3.1 Move `CsvParser` and the dump downloader to `infrastructure`; add `TuneCatalog.findBySourceId` (bulk map per run)
- [x] 3.2 `AlbumDumpImport` with state/counters, `POST/GET /api/albums/import-dump` (Administrator, 202/401/403/409), idempotent, unclassified Artists, per-Album transactions
- [x] 3.3 Tests with a fake dump: all known, unknown Tune leaves no Set, rerun, non-Administrator 403, download failure `failed`
- [x] 3.4 `live` forwards `AlbumChanged`; `search` indexes Albums; update `package-info.java` allowed dependencies; `ModularApplicationTests` passes

## 4. Attach audio (commit: `feat(track): attach uploaded audio to a catalogue track`)

- [ ] 4.1 `POST /api/tracks/{id}/audio`, `catalogueTrackId` on upload, 409 when audio exists
- [ ] 4.2 `GET /api/tracks/{id}/catalogue-suggestions` (same Artist, normalised title)
- [ ] 4.3 Tests for attach, conflict, direct attach at upload and suggestions

## 5. UI (commit: `feat(ui): browse albums and attach audio to catalogue tracks`)

- [ ] 5.1 `albums.svelte.ts` store with `refreshOnLive`; Albums page and Album view (keyboard reachable, "No audio yet" text, theme tokens only)
- [ ] 5.2 Create-Album form; upload form choice of Catalogue Track; suggestion banner (`aria-live`) with accept
- [ ] 5.3 Tune view lists Albums (spec `tune`)
- [ ] 5.4 vitest and Playwright tests (mocked with `page.route`); `bun run format`, `lint`, `check`

## 6. Docs and finish

- [ ] 6.1 Update `GLOSSARY.md`: Track (audio not always present), Performance and Set wording for unresolved entries, Catalogue Track
- [ ] 6.2 Update `README.adoc` features and retake screenshots if the UI changed
- [ ] 6.3 Run `./mvnw clean verify`
- [ ] 6.4 Open the PR titled `feat(track): import albums and catalogue tracks from thesession.org recordings`, body says `Closes #847`
