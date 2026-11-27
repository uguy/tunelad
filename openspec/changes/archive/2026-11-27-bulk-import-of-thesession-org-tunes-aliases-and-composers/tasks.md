## 1. Branch

- [x] 1.1 Create `feat/tune-bulk-import` from an up-to-date `main`

## 2. Dump reading (commit: `feat(tune): read the thesession.org csv dump`)

- [x] 2.1 Confirm the live column names of `tunes.csv` and `aliases.csv` (composer in particular) and record them in design.md
- [x] 2.2 Add the `TuneDump` source streaming both files reactively, with a configurable base URL
- [x] 2.3 Map rows to `SourceTune` (first setting only, composer as author, aliases merged, bad rows skipped); reuse the ABC helpers
- [x] 2.4 Unit tests for the mapping and CSV quoting, with a stubbed server

## 3. Import job and API (commit: `feat(tune): bulk import tunes from the dump`)

- [x] 3.1 Batched idempotent insertion and the in-memory run state
- [x] 3.2 `POST` and `GET /api/tunes/import-dump`, Administrator only, 409 while running; update the OpenAPI description
- [x] 3.3 Testcontainers tests: import, run again, resume, skipped rows, 403/401/409, failed download

## 4. UI (commit: `feat(ui): thesession.org credit and import trigger`)

- [x] 4.1 Footer credit "Data from thesession.org" with theme tokens
- [x] 4.2 Administrator trigger and progress display, accessible
- [x] 4.3 Playwright e2e tests (mocked API) and a palette check for the credit

## 5. Wrap up

- [x] 5.1 Update `GLOSSARY.md` if a term changed (Tune's author now may come from the dump)
- [x] 5.2 Update `README.adoc` features
- [x] 5.3 Confirm thesession.org's terms of reuse and adjust the credit accordingly
- [x] 5.4 Run `./mvnw clean verify`
- [x] 5.5 Open the PR titled `feat(tune): bulk import thesession.org tunes, aliases and composers` with `Closes #846` in the body
