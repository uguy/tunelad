# Tasks

## 1. Branch

- [x] 1.1 Create `feat/derive-set-from-audio` from an up-to-date `main`

## 2. Sidecar: open-ended mode

- [x] 2.1 `server.ts`: accept `POST /identify` without `tunes` and report every detection; keep the 400s for a missing body
- [x] 2.2 Sidecar tests (`identify.test.ts`) for the open-ended request, and README: API and the measured time of the open-ended pass
- [x] 2.3 Commit `feat(identification): let the sidecar recognise any tune`

## 3. Backend: derive and attach (`identification`, `track`, `set`, `tune`)

- [x] 3.1 `tune`: `TuneCatalog.findOrImport(AccountRef, int)`, using the existing import, empty (logged) when the Tune cannot be fetched
- [x] 3.2 `set`: `SetCatalog.findOrCreate(AccountRef, List<String>)` delegating to `SetService.create`; add `set` to `identification`'s `allowedDependencies`
- [x] 3.3 `track`: `AttachSet` command and `TrackService.execute(AttachSet)` under the row lock, only for a Track still without a Set, building identified Performances and publishing `TrackUpdated`
- [x] 3.4 `identification`: `IdentificationSource.detectAny`, `CadenceSource` implementation, `min-confidence` property, `SetDerivation` (filter, merge, repeats, cap), and the Set-less branch of `IdentificationService.identify`; update the re-run's OpenAPI description
- [x] 3.5 Tests: `SetDerivation` unit tests; API tests with a stubbed sidecar for two tunes, existing Set, other order, one tune, weak detections, split and repeated detections, over twenty, nothing heard, unknown Tune imported (stubbed thesession.org), one Tune not importable, a Track that has a Set (restricted request), a Set arriving first, service down, re-run on a Set-less Track, `track.indexed` and a Track fetched with its Set
- [x] 3.6 `ModularApplicationTests` still passes
- [x] 3.7 Commit `feat(identification): derive a track's set from its audio`

## 4. Frontend

- [x] 4.1 vitest: the details of a Track without a Set show the Tunes section once the refetched Track has a Set; fix `TrackList` if they do not refresh
- [x] 4.2 Playwright e2e (API mocked): `track.indexed` on an open Set-less Track shows its Tunes and start offsets
- [x] 4.3 `bun run format`, lint and check; `svelte-autofixer` on changed components
- [x] 4.4 Commit `feat(ui): show a set derived from the audio`

## 5. Docs

- [x] 5.1 `GLOSSARY.md`: Identification can also derive the Set of a Track that has none
- [x] 5.2 Check `docs/adr` for a decision this contradicts; add an ADR for deriving Sets from open-ended recognition only if the check says one is needed
- [x] 5.3 `README.adoc`: features (derived Set, `min-confidence`, imports), identification section
- [x] 5.4 Commit `docs(identification): describe the derived set`

## 6. Verify and ship

- [x] 6.1 `./mvnw clean verify` passes
- [x] 6.2 Archive the change on the branch (`/openspec-archive-change derive-set-from-audio`), syncing specs
- [x] 6.3 Push and open the PR titled `feat(identification): derive a track's set from its audio`, body ending `Closes #841`
