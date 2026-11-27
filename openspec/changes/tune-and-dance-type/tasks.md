## 1. Branch and module skeleton

- [x] 1.1 Create branch `feat/tune-and-dance-type` from an up-to-date `main`
- [x] 1.2 Add `org.tunelad.tune` with `package-info.java` (allowing `infrastructure`, `account`), `tune.sql` changelog included in the master changelog; `ModularApplicationTests` passes. Commit `feat(tune): add tune module and schema`

## 2. Tunes and Dance types API

- [x] 2.1 `DanceType` enum, `Tune` entity, repository, service; `GET /api/dance-types`, `POST /api/tunes`, `GET /api/tunes` (filters), `GET /api/tunes/{id}` with OpenAPI annotations
- [x] 2.2 Integration tests (Testcontainers) for create, validation errors, 401, filters and alias search. Commit `feat(tune): create and browse tunes`

## 3. thesession.org import

- [x] 3.1 `TuneSource` interface and WebClient implementation, `POST /api/tunes/import`, idempotent on `source_id`, 404/502 mapping
- [x] 3.2 Tests with a stubbed thesession.org (MockWebServer or stub bean): import, import twice, unknown id, upstream failure. Commit `feat(tune): import tunes from thesession.org`

## 4. UI

- [x] 4.1 Routes `tunes`, `tunes/[id]`, `tunes/new`, header link, theme tokens, accessible labels and focus handling
- [x] 4.2 `AbcViewer.svelte`: lazy `abcjs` import, sheet/ABC text switch (default sheet, no-transcription message), Play/Stop/tempo, pauses the Track player, self-hosted soundfont, error state. Commit `feat(ui): render and play tune transcriptions`
- [x] 4.3 vitest tests (abcjs mocked) for the viewer logic, plus Playwright switch and Play/Stop state tests; check the main bundle did not grow. `bun run format`.
- [x] 4.4 vitest tests for helpers and Playwright e2e (API mocked with `page.route`) for list, filter, detail and import. `bun run format`. Commit `feat(ui): add tunes pages`

## 5. Docs and PR

- [x] 5.1 Update `GLOSSARY.md` Tune definition (identity wording, see design) and README.adoc features (sheet music view and MIDI playback). Commit `docs(tune): document tunes`
- [x] 5.2 Run `./mvnw clean verify`
- [ ] 5.3 Open the PR titled `feat(tune): add tunes with dance types`, body including `Closes #817`
