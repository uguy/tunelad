# Tasks

## 1. Branch

- [x] 1.1 Create the feature branch `feat/live-ui-updates` from an up-to-date `main`; verify with `git branch --show-current` and that `git log main..HEAD` is empty

## 2. NATS in the build and the tests

Commit: `build(live): add nats to docker compose and the test containers`

- [x] 2.1 Add the `io.nats:jnats` dependency to `pom.xml`; verify `./mvnw -q compile -DskipTests` succeeds
- [x] 2.2 Add a `nats` service (no JetStream, port 4222) to `docker-compose.yaml`; verify `docker compose up -d nats` starts it and `docker compose ps nats` shows it running
- [x] 2.3 Add `tunelad.live.nats.url` (default `nats://localhost:4222`) to `application.yml` with a properties class `LiveProperties` (binding to `tunelad.live`); verify the binding in a small test that loads the default and an overridden URL
- [x] 2.4 Add `NatsContainerDefinition` (a `GenericContainer` on the NATS image, exposing 4222, registering `tunelad.live.nats.url` from the mapped port) in `src/test/java/org/tunelad/`, next to `PostgresContainerDefinition`, and import it from `TestContainerDefinitions`; verify an existing API test (for example `TrackApiTest`) still starts and passes

## 3. Events from the modules that own the state

Commit: `feat(search): announce tracks that become or stop being searchable` and `feat(media): announce computed waveforms`, as two commits

- [x] 3.1 In `search`, add the package `org.tunelad.search.event` with `@NamedInterface("event")` and the records `TrackIndexed(String trackId)` and `TrackUnindexed(String trackId)`; verify `ModularApplicationTests` passes
- [x] 3.2 Publish `TrackIndexed` from `SearchProjection` after the search copy is written for `NewTrackSaved` and `TrackUpdated`, and `TrackUnindexed` after the copy is removed for `TrackDeleted`, using `ApplicationEventPublisher`; verify with tests in `src/test/java/org/tunelad/search/` that an upload and an edit raise `TrackIndexed` only after a search finds the track (spec: Announce tracks that become searchable) and that a delete raises `TrackUnindexed` after a search no longer finds it
- [ ] 3.3 In `media`, add the package `org.tunelad.media.event` with `@NamedInterface("event")` and the record `WaveformComputed(String key)`; verify `ModularApplicationTests` passes
- [ ] 3.4 Make `Waveforms.complete` report whether it updated a row, and publish `WaveformComputed` for `READY` and `FAILED` results in the same reactive transaction as the save, through `TransactionalEvents`, from both the `AudioStored` listener and `WaveformBackfill`; verify with tests in `src/test/java/org/tunelad/media/` that the event is raised for a successful upload, a failed decode and a backfilled waveform, and is not raised while ffmpeg is missing (stays `PENDING`) or after the audio was deleted (spec: Announce a computed waveform)

## 4. The `live` module: NATS bridge, router and SSE endpoint

Commit: `feat(live): stream live updates through nats and server-sent events`

- [ ] 4.1 Create the `live` module (`package-info.java` with `allowedDependencies = { "search::event", "media::event", "account", "infrastructure" }`, packages `internal` and `web`); verify `ModularApplicationTests` passes and `ModuleTablesTest` is unaffected
- [ ] 4.2 Implement the NATS connection (asynchronous connect, unlimited reconnects, no or a very small reconnect buffer, closed on shutdown), and a publisher that catches and logs every failure; verify with a test that starts the context with an unreachable NATS URL (application starts, a publish returns without throwing)
- [ ] 4.3 Implement the bridge listeners (`@ApplicationModuleListener`) for `TrackIndexed`, `TrackUnindexed` and `WaveformComputed` publishing `{"type","id"}` on `tunelad.library.<type>`, mapping the media key to the track id; verify with a test that a raised event arrives on a raw NATS subscription with the expected subject and body
- [ ] 4.4 Implement `LiveRouter` (one subscription to `tunelad.>`, audience from the subject, account key as unpadded base64url, body forwarded verbatim, emission into a `directBestEffort` multicast sink) and the audience filter; verify with unit tests (no containers) for library, account, malformed subjects and ids containing `.`, `*`, `>` and spaces
- [ ] 4.5 Implement `GET /api/live` (`text/event-stream`, event name = type, 15 s comment heartbeat, audience from `CurrentAccount`, no client-supplied input read) with its OpenAPI description; verify with integration tests in `src/test/java/org/tunelad/live/` for each scenario of the `live` spec: anonymous and authenticated open, event shape, library event to all, account event to every connection of that account and nobody else (published directly on NATS), `?account=` ignored, slow client not holding up another, heartbeat (heartbeat interval made configurable for the test)
- [ ] 4.6 Add integration tests of the whole path: an upload yields `track.indexed` and a following search finds the track; a delete yields `track.unindexed`; an upload with the messaging infrastructure unavailable still returns 201, and delivery resumes when NATS comes up (start the container after the context on a fixed port); verify they pass
- [ ] 4.7 Add the two-instance test (two Spring contexts on random ports, shared PostgreSQL and NATS containers: a client on the first instance receives `waveform.computed` raised on the second); verify it passes, falling back as described in design.md if it proves flaky

## 5. Live connection in the UI

Commit: `feat(ui): keep a live connection to the server`

- [ ] 5.1 Add `app/src/lib/live.svelte.ts` (single `EventSource('/api/live')`, `on(type, handler)` returning an unsubscribe function, a reconnect notification, reopen when the account changes) and start it from `app/src/routes/+layout.svelte`; verify with vitest tests using a fake `EventSource`: one connection across navigation, handlers called by type and removed on unsubscribe, reconnect notification after an error then open, reopen on login and logout
- [ ] 5.2 Run `cd app && bun run format && bun run lint && bun run check`; verify they pass

## 6. Live tracks list and waveforms

Commit: `feat(ui): refresh the tracks list and waveforms from live updates`

- [ ] 6.1 In the tracks page, refetch the list with the search text currently entered on `track.indexed` and `track.unindexed` (debounced about 300 ms) and on reconnect; verify with vitest tests that a burst of events causes one request carrying the active search text, and with a test that the list holds an own uploaded or deleted track exactly once / not at all
- [ ] 6.2 In `TrackList.svelte`, remove the polling (`POLL_INTERVAL`, `POLL_ATTEMPTS`), refetch a waveform on `waveform.computed` for a track already requested, ignore tracks never requested, and refetch the requested ones on reconnect; verify with vitest tests: placeholder replaced when the event arrives (also minutes later, with fake timers), off-screen track not fetched, no timer-based refetch remains
- [ ] 6.3 Add Playwright e2e tests in `app/e2e/` that replace `EventSource` with a controllable fake through `page.addInitScript` and mocked API routes: a waveform appears without a reload after `waveform.computed`; another user's new, renamed and deleted track appear, change and disappear after the matching events; the list refetches after a simulated reconnect; verify `cd app && bun run test:e2e` passes
- [ ] 6.4 Run `cd app && bun run format && bun run lint && bun run check && bun run test:unit -- --run`; verify they pass

## 7. Wrap up

- [ ] 7.1 Update the `== Features` section of `README.adoc` (live updates; NATS in the local setup and the `docker compose` command) ; verify the text matches the delivered behaviour
- [ ] 7.2 Run `./mvnw clean verify` (frontend lint and check, all Java tests, vitest, Playwright e2e); verify it passes, with Docker running
- [ ] 7.3 Push the branch and open a PR to `main` titled `feat(live): push live ui updates through nats and server-sent events`, only after 7.2 passes; verify the `workflow-pr-title` check passes

## Workflow follow-up

- Archive the change on the feature branch before merge (`/openspec-archive-change`), so the specs land in `main` with the code.
