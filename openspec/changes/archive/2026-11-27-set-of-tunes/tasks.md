## 1. Branch and module skeleton

- [x] 1.1 Create branch `feat/set-of-tunes` from an up-to-date `main`
- [x] 1.2 Add `TuneCatalog` to `org.tunelad.tune` (implemented by `TuneService`), `org.tunelad.set` with `package-info.java` (allowing `infrastructure`, `account`, `tune`), `set.sql` included in the master changelog, `set_*` tables added to the truncate in `AbstractApiTest`; `ModularApplicationTests` and `ModuleTablesTest` pass. Commit `feat(set): add set module and schema`

## 2. Sets API

- [x] 2.1 `POST /api/sets` get-or-create (201/200, validation, 401), `GET /api/sets` (with `tuneId`), `GET /api/sets/{id}`, OpenAPI annotations, security rule
- [x] 2.2 Testcontainers tests per spec scenario, including two concurrent identical creates ending with one Set. Commit `feat(set): create and browse sets`

## 3. UI

- [x] 3.1 Routes `sets`, `sets/[id]`, `sets/new` (builder with search, add, up/down, remove, live region), header link, theme tokens, accessibility
- [x] 3.2 vitest tests for list-editing helpers and Playwright e2e (API mocked with `page.route`) for build, reorder, existing Set and browse. `bun run format`. Commit `feat(ui): add sets pages`

## 4. Docs, verification, PR

- [x] 4.1 Check `GLOSSARY.md` still matches (Set definition, update if a term changed) and add Sets to the README.adoc features. Commit `docs(set): document sets`
- [x] 4.2 Run `./mvnw clean verify`
- [x] 4.3 Archive the change on the feature branch (`/openspec-archive-change set-of-tunes`) so its spec lands in `main` with the code. Commit `docs(openspec): archive set-of-tunes`
- [x] 4.4 Push and open the PR titled `feat(set): add sets of tunes`, body including `Closes #818`
