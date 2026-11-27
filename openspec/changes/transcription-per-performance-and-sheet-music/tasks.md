## 1. Branch and storage

- [x] 1.1 Branch `feat/tune-transcriptions` from up-to-date main (already created locally); verify with `git branch --show-current`.
- [x] 1.2 Add the `abc` column changeset to `track.sql`, `Performance`, `PerformanceInput` (limit 20000, validated), `PerformanceDTO` and the mapper/edit flow; extend `TrackPerformancesTest` for writing, clearing and the 400 on overlength. Commit `feat(track): store a transcription on each performance`.

## 2. Performances of a Tune

- [x] 2.1 Add `TrackService.findPerformancesByTune`, `TunePerformanceDTO` and `GET /api/tunes/{id}/performances` in `track`; keep `ModularApplicationTests` green; test across Tracks, empty and anonymous access with Testcontainers; check the OpenAPI output lists the new path and `abc`. Commit `feat(track): list the performances of a tune`.

## 3. UI

- [x] 3.1 Add an ABC textarea (monospace, limit shown) to `PerformanceFields.svelte`, with `abc` in `Performances.ts` (`checkPerformance`, `toInput`); vitest in `Performances.test.ts` and a Playwright flow mocking the API with `page.route`. Commit `feat(ui): edit a performance's transcription`.
- [x] 3.2 Fetch performances in `Tunes.ts` and render a sheet per Performance on `tunes/[id]/+page.svelte` with Reference transcription fallback; vitest plus Playwright covering sheets, fallback and none. Commit `feat(ui): show transcriptions on the tune view`.

## 4. Docs and delivery

- [x] 4.1 Check `GLOSSARY.md` (Transcription, Reference transcription) still matches and update it if a term changed; update `README.adoc` features if user-visible and retake screenshots if the Tune page changed. Commit `docs: describe performance transcriptions`.
- [x] 4.2 Run `./mvnw clean verify` and confirm it passes.
- [x] 4.3 Push and open the PR titled `feat(tune): show transcriptions on the tune view`, body ending with `Closes #820`; move the card to In review.
