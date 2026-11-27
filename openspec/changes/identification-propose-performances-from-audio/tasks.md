## 1. Research and branch

- [x] 1.1 Create branch `feat/identification-propose-performances` from an up-to-date `main` (already done by `/next-slice`)
- [x] 1.2 Research the sources and run the spikes; `docs/research/identification-sources.md`. Commits `docs(identification): ...`
- [x] 1.3 Update ADR 0001 consequences and `GLOSSARY.md` (Identification: proposes Performances' start offsets; key and Transcription later). Commit `docs(identification): record sidecar decision`

## 2. Performance state in track

- [x] 2.1 Liquibase changeset: `identification` column on `track_performances` (existing rows `corrected`)
- [x] 2.2 `Performance`, `PerformanceDTO` (`identification`, OpenAPI enum) and mapping; new Performances start `pending`
- [x] 2.3 `UpdateTrackCommand` marks changed Performances `corrected`; add `ProposePerformances` command in `track::command` that skips `corrected` ones
- [x] 2.4 Tests (Testcontainers): upload state, edit marks corrected, unchanged save keeps state, proposal never overwrites corrected. Commit `feat(track): record identification state of performances`

## 3. Analysis sidecar

- [x] 3.1 `sidecar/identification/`: Node wrapper (`POST /identify`, `GET /health`), engine copied from Cadence at a pinned commit with `LICENSE` and `NOTICE`, ffmpeg decoding, index cached on a volume
- [x] 3.2 Dockerfile, `docker-compose.yml` service and volume, CI builds the image
- [x] 3.3 Smoke test on `Stephen_Doherty_Patrick_Doocey-Mossy_banks_Ravens_rock.mp3`: "The Mossy Banks" detected at about 7.5 s; golden test on a small fixture. Commit `feat(identification): add analysis sidecar`

## 4. Identification module

- [x] 4.1 `identification` package with `package-info.java` (`allowedDependencies`), `IdentificationSource` port, `CadenceSource` (`WebClient`, streamed audio, timeouts) and its properties
- [x] 4.2 Listener on `NewTrackSaved` running Identification asynchronously, keeping only detections of the Set's Tunes and applying them through `ProposePerformances`
- [x] 4.3 `POST /api/tracks/{id}/identification` (202/403/404) with OpenAPI annotations
- [x] 4.4 Tests with a stubbed sidecar: upload returns first, proposal applied, Tune outside the Set ignored, nothing detected, service down, re-run keeps corrections, permissions. Run `ModularApplicationTests`. Commit `feat(identification): propose performances from audio`

## 5. UI

- [x] 5.1 State badge and re-run button on the tune view; theme tokens, accessible text; `bun run format`, `lint`, `check`
- [x] 5.2 vitest/Playwright tests with `page.route` mocks. Commit `feat(ui): show identification state of performances`

## 6. Wrap-up

- [x] 6.1 Update `README.adoc` features and the sidecar's setup (screenshots: no page they show changed)
- [x] 6.2 Comment on #823: key and Transcription proposals move to a later slice
- [x] 6.3 Run `./mvnw clean verify` and fix failures
- [ ] 6.4 Open the PR titled `feat(identification): propose performances from audio` with `Closes #823` in the body
