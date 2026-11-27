# Tasks

## 1. Branch

- [ ] 1.1 Create or confirm feature branch `feat/band-and-musician` from an up-to-date `main`

## 2. Classification and membership in the track module (commit: `feat(track): classify artists as musicians and bands`)

- [x] 2.1 Liquibase changeset in `track.sql`: `kind` column, `track_band_members` table, rollback
- [x] 2.2 `ArtistKind`, extend `Artist` and `Artists`/`DbArtists` (find, setKind, members, bands, replaceMembers in one transaction) and the rules from the design
- [x] 2.3 `ArtistController` (`GET`/`PUT /api/artists/{id}`, `PUT .../members`), `ArtistDTO`, OpenAPI annotations
- [x] 2.4 `TunePerformanceDTO` gains `artistId` and `artistKind`; update mapping
- [x] 2.5 Integration tests (Testcontainers) for every scenario in `specs/track`; `ModularApplicationTests` still passes

## 3. UI: Recorded by (commit: `feat(ui): show who recorded a tune`)

- [x] 3.1 Regenerate/update the API client types for `ArtistDTO` and `TunePerformance`
- [x] 3.2 "Recorded by" section on the Tune page with read-only view for visitors and classify/members controls for Accounts (accessible labels, theme tokens)
- [x] 3.3 Vitest for the distinct-Artist derivation; Playwright e2e (mocked API) for the `specs/tune` scenarios
- [x] 3.4 `bun run format`, `bun run lint`, `bun run check`

## 4. Docs and wrap-up (commit: `docs(track): document artist kinds`)

- [x] 4.1 Update `GLOSSARY.md` if a term's wording changed (Artist, Band, Musician)
- [x] 4.2 Update `README.adoc` features and retake screenshots if the UI changed visibly
- [x] 4.3 Run `./mvnw clean verify` and fix failures
- [ ] 4.4 Open the PR titled `feat(track): classify artists as musicians and bands`, body ending with `Closes #845`
