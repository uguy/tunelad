## 1. Branch

- [x] 1.1 Branch `feat/live-tunes-sets-performances` created from an up-to-date `main` (done by `/next-slice`).

## 2. Backend events

- [x] 2.1 `tune::event.TuneChanged` and `set::event.SetChanged` (named interface packages); publish on Tune create/import (only when created), on bulk import end (one event, no id) and on Set create. Verify: service/integration tests that each action publishes exactly one event and an import of an existing Tune publishes none. Commit `feat(tune): publish tune and set changes`.
- [ ] 2.2 `LiveBridge` publishes `tune.changed`, `set.changed` and `performance.changed` (from `NewTrackSaved`, `TrackUpdated`, `TrackDeleted`) to everyone; `live` `allowedDependencies` and Javadoc updated; `GET /api/live` OpenAPI description lists the types. Verify: live tests cover every scenario of `specs/live` (anonymous and authenticated receive, no content, id-less bulk import event, second instance receives); `ModularApplicationTests` passes. Commit `feat(live): publish events for the new entities`.

## 3. UI

- [ ] 3.1 `refreshOnLive` on the Tunes list, Tune page (Tune and Performances), Sets list and Set page, updating in place. Verify: vitest component tests and Playwright e2e (API mocked with `page.route`) for each UI scenario of `specs/live`, including reconnect; `bun run lint`, `bun run check`, `bun run format`. Commit `feat(ui): follow tunes, sets and performances live`.

## 4. Docs and delivery

- [ ] 4.1 Update `GLOSSARY.md` (`Live update` lists Tunes, Sets and Performances) and README.adoc features if user-visible. Verify: terms match the specs. Commit `docs(live): describe live updates for tunes, sets and performances`.
- [ ] 4.2 Run `./mvnw clean verify`; it must pass. Verify: exit code 0.
- [ ] 4.3 Push and open the PR titled `feat(live): publish events for the new entities`, body ending with `Closes #824`. Verify: PR exists and `workflow-pr-title` passes.
