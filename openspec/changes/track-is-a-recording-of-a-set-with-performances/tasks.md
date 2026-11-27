## 1. Branch and module wiring

- [x] 1.1 Create branch `feat/track-set-performances` from an up-to-date `main` (already created for this change: check it is current)
- [x] 1.2 Add `SetCatalog` to `org.tunelad.set` (implemented by `SetService`: `findById`, `findAllById`), allow `set` in `track`'s `package-info.java`; add `track_performances` and `track_tracks.set_id` to `track.sql` with rollback, and both to the truncate in `AbstractApiTest`; `ModularApplicationTests` and `ModuleTablesTest` pass. Commit `feat(track)!: add set and performances schema`

## 2. Tracks API

- [x] 2.1 `PerformanceInput` and validation, `Performance` rows and `Performances` repository, `Track.setId`; `AddTrackCommand`/`UpdateTrackCommand`/`TrackEdit` extended; `TrackController.create` takes `setId` and `performances`; edit takes `performances`; `TrackDTO` gains `set` and `performances` (`PerformanceDTO`), `TrackMapper` batches the reads
- [x] 2.2 OpenAPI annotations for the new parts, fields and 400 causes
- [x] 2.3 Testcontainers tests per spec scenario (upload with and without `performances`, unknown Set, invalid performances, legacy Track without a Set, renamed Tune, edit Performances, not the owner, delete with Playlist/Favourite cleanup and Set kept) and update existing upload tests and helpers to send a `setId`. Commit `feat(track)!: record tracks as sets with performances`

## 3. UI

- [x] 3.1 Extract `SetBuilder.svelte` from `sets/new` (behaviour unchanged, existing tests still pass); upload page with Set select or builder, per-Tune Performance fields, `m:ss` helper in `lib` with a vitest test
- [x] 3.2 Track page shows the Set and Performances; track edit form edits the Performances; types in `lib/api.ts`
- [x] 3.3 Playwright e2e (API mocked with `page.route`): upload with existing Set, upload with a new Set, missing Set, Performance fields follow the Set, track page, edit; update existing upload e2e tests. `bun run format`. Commit `feat(ui)!: choose a set and performances when uploading`

## 4. Docs, verification, PR

- [x] 4.1 Check `GLOSSARY.md` against the change (Track, Performance, Set; note that Tracks from before have no Set if the wording needs it) and update it if a term changed; add Performances to the README.adoc features; retake screenshots if the UI or Scalar view changed (`node doc/screenshots.mjs`, `node doc/api-screenshots.mjs`). Commit `docs(track): document sets and performances`
- [x] 4.2 Run `./mvnw clean verify`
- [ ] 4.3 Archive the change on the feature branch (`/openspec-archive-change track-is-a-recording-of-a-set-with-performances`). Commit `docs(openspec): archive track-is-a-recording-of-a-set-with-performances`
- [ ] 4.4 Push and open the PR titled `feat(track)!: record tracks as sets with performances`, body including `Closes #819`
