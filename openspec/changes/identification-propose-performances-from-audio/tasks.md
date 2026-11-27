## 1. Research and branch

- [x] 1.1 Create branch `feat/identification-propose-performances` from an up-to-date `main` (already done by `/next-slice`)
- [ ] 1.2 Research FolkFriend and irishtune.info (API, auth, licence, limits, band recordings); write `docs/research/identification-sources.md`; amend ADR 0001 and `design.md` if findings change them. Commit `docs(identification): research proposal sources`

## 2. Performance state in track

- [ ] 2.1 Liquibase changeset: `identification` column on `track_performances` (existing rows `corrected`)
- [ ] 2.2 `Performance`, `PerformanceDTO` (`identification`, OpenAPI enum) and mapping; new Performances start `pending`
- [ ] 2.3 `UpdateTrackCommand` marks changed Performances `corrected`; add `ProposePerformances` command in `track::command` that skips `corrected` ones
- [ ] 2.4 Tests (Testcontainers): upload state, edit marks corrected, unchanged save keeps state, proposal never overwrites corrected. Commit `feat(track): record identification state of performances`

## 3. Identification module

- [ ] 3.1 `identification` package with `package-info.java` (`allowedDependencies`), `IdentificationSource` port, adapters from the research, `WebClient` config and timeouts
- [ ] 3.2 Listener on `NewTrackSaved` running Identification asynchronously and applying proposals through `ProposePerformances`
- [ ] 3.3 `POST /api/tracks/{id}/identification` (202/403/404) with OpenAPI annotations
- [ ] 3.4 Tests with a stubbed source server: upload returns first, proposals applied, no proposal, source down, re-run keeps corrections, permissions. Run `ModularApplicationTests`. Commit `feat(identification): propose performances from audio`

## 4. UI

- [ ] 4.1 State badge and re-run button on the tune view; theme tokens, accessible text; `bun run format`, `lint`, `check`
- [ ] 4.2 vitest/Playwright tests with `page.route` mocks. Commit `feat(ui): show identification state of performances`

## 5. Wrap-up

- [ ] 5.1 Update `GLOSSARY.md` if the research changes a term (Identification, Performance states)
- [ ] 5.2 Update `README.adoc` features, retake screenshots (`doc/screenshots.mjs`, `doc/api-screenshots.mjs`)
- [ ] 5.3 Run `./mvnw clean verify` and fix failures
- [ ] 5.4 Open the PR titled `feat(identification): propose performances from audio` with `Closes #823` in the body
