# Tasks

## 1. Branch and local MongoDB replica set

Commit: `build: run local mongo as a single-node replica set`

- [x] 1.1 Create branch `refactor/search-read-model` from an up-to-date `main` (`git switch main && git pull && git switch -c refactor/search-read-model`). Verify that `git branch --show-current` prints it.
- [x] 1.2 Change the `mongo` service in `docker-compose.yaml` so it runs as a single-node replica set with root auth kept: generate a key file in the entrypoint, start with `--replSet rs0 --keyFile`, and run `rs.initiate()` once from a healthcheck or init script. Point `mongo-express` at the replica set. Adjust the connection settings in `application.yml` (e.g. `directConnection=true`) if the app cannot connect. Verify: after `docker compose down -v && docker compose up -d`, `docker exec mongo mongosh -u tunelad -p tunelad --quiet --eval 'rs.status().ok'` prints `1`, and `./mvnw spring-boot:run` starts and serves `GET /api/tracks`.
- [x] 1.3 Update the local-setup part of `README.adoc` to say that Mongo runs as a replica set (needed for transactions), and that `docker compose down -v` is how to wipe local data. Verify that the documented commands run as written.

## 2. Remove delete-all from the public API

Commit: `refactor(track)!: remove the delete-all tracks endpoint`

- [x] 2.1 Replace the HTTP reset in `AbstractApiTest.clearLibrary()` with a direct reset (design D0): through `ReactiveMongoTemplate`, delete the documents of every non-system collection in the test database, blocking in `@BeforeEach`, and keep the indexes. Verify that `./mvnw -DskipITs test -Dtest='TrackApiTest,TrackSearchApiTest'` passes before any endpoint is removed.
- [x] 2.2 Delete `DELETE /api/tracks` from `TrackController`, `TrackService.execute(DeleteAllTrackCommand)` and its implementation, `Tracks.deleteAll()` and its `DbTracks` implementation, `DeleteAllTrackCommand`, `AllTracksDeleted`, and the `on(AllTracksDeleted)` methods in `TrackEventHandler` and `DefaultTrackEventHandler`. Remove the `clear_a_populated_library` test from `TrackApiTest`. Add a test asserting that `DELETE /api/tracks` responds 405 Method Not Allowed. Verify that `TrackApiTest` and `ModularApplicationTests` pass, that `grep -rn "DeleteAllTrackCommand\|AllTracksDeleted\|deleteAll" src/main` finds nothing, and that `/v3/api-docs` has no `delete` operation under `/api/tracks`.

## 3. Spike: reactive transactions and the Event Publication Registry

Commit: `feat(track): publish track events within reactive mongo transactions`

- [x] 3.1 Add a `ReactiveMongoTransactionManager` bean in an `infrastructure` configuration class (design D3). Add `spring-modulith-events-mongodb` (or the Modulith 2.1 starter that provides the Mongo registry) and `spring-modulith-starter-test` (test scope). Verify that `./mvnw -DskipITs test -Dtest=TrackApiTest,ModularApplicationTests` passes, now that `@Transactional` takes effect.
- [x] 3.2 Write a spike test in the infrastructure test package, with a test-only `@Transactional` reactive service and an `@ApplicationModuleListener`. It answers design D1 questions 1 and 4: an event published with `TransactionalEventPublisher` reaches the listener after the commit, on a non-Netty thread, and never reaches it when the transaction errors and rolls back. Verify that the test passes against Testcontainers Mongo. It stays as a regression test.
- [x] 3.3 Extend the spike to answer D1 questions 2 and 3: log or assert the thread that writes the registry's publication record, and check whether a rolled-back transaction leaves a publication document behind. If the write blocks on a Netty thread, apply design D4 (publish on `boundedElastic`) and add an assertion (e.g. a thread-name check) that fails if the record is written on a `reactor-http` thread. Verify that the spike test passes with that assertion.
- [x] 3.4 Record the spike's answers to D1 questions 1–4 in `design.md` (D1, D4, Risks), and whether option 3 or the D8 fallback applies. If it is the fallback, revert 3.1's registry dependency and the transaction manager, rewrite 3.2 as a plain `@EventListener` delivery test, and follow D8 in the next groups. Verify that `design.md` has no unanswered spike question left.
- [x] 3.5 Switch `DefaultTrackService` to publish `NewTrackSaved` through `TransactionalEventPublisher` as part of the reactive chain (design D2; with D8, keep the plain publisher). Verify with a test (Modulith `PublishedEvents` or `@RecordApplicationEvents`) that an upload publishes `NewTrackSaved` exactly once, with the stored track's fields. `TrackApiTest` still passes.

## 4. Search read model and projection

Commit: `feat(search): project track events into a search read model`

- [x] 4.1 In `org.tunelad.search`, add the read-model document (`search_tracks`: `id, title, description, artist, album, format`, with a compound text index on `title` and `description`), its reactive repository, and a query that returns a `$text` match sorted by score, or every document for a blank query (design D5). All of this goes in an internal package. Verify that `ModularApplicationTests` passes.
- [x] 4.2 Add `SearchProjection` with an `@ApplicationModuleListener` that upserts by track id on `NewTrackSaved`, blocking on the listener thread (design D6; with D8, use `@EventListener` returning `Mono<Void>`). Set `search/package-info.java` `allowedDependencies` to `{ "track", "track::event" }`. Verify with an `@ApplicationModuleTest` for `search` that uses the `Scenario` API: publishing `NewTrackSaved` makes the document appear with the event's fields, and publishing it twice leaves one document. `ModularApplicationTests` passes.

## 5. Serve search from the read model

Commit: `refactor(search): serve track search from the read model`

- [x] 5.1 Add `SearchController` in `org.tunelad.search.web` mapping `GET /api/tracks/search?q=` to the read-model query, returning a search-owned DTO (`TrackSearchResult`) with the same JSON fields as `TrackDTO`. Add a `Search` OpenAPI tag, a summary, a description that mentions eventual consistency, and a documented `q` parameter (design D7). Verify that `/v3/api-docs` shows the operation under the `Search` tag with the same path and parameter, and a response schema with the same properties as `Track`.
- [x] 5.2 Remove search from `track`: the endpoint in `TrackController`, `searchTracks` from `TrackService`, `DefaultTrackService`, `Tracks` and `DbTracks`, and `@TextIndexed` from `Track`. Verify that `./mvnw -DskipITs test` compiles and `TrackApiTest` passes. Also verify that `grep -rn "searchTracks\|TextIndexed" src/main/java/org/tunelad/track` finds nothing.
- [x] 5.3 Update `TrackSearchApiTest` to the new behaviour (design D9): wait with Awaitility (up to 5 s) for uploads to become searchable before asserting. Add one test per new scenario: "New track found after a short delay" and "Searchable track has the track list fields". Update the class Javadoc, which still says search is served by the track module. Verify that all existing and new `TrackSearchApiTest` tests pass.
- [x] 5.4 Run the frontend e2e search flow against the new endpoint with `./mvnw verify -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false` (or the narrowest e2e invocation available). Verify that the Playwright tests that use the track list or search still pass with no change in `app/`.

## 6. Verify, document, open the PR

- [ ] 6.1 Run `./mvnw clean verify` with Docker running. Verify that it ends in `BUILD SUCCESS` (Java tests, vitest and Playwright e2e).
- [ ] 6.2 Update `README.adoc`: the MongoDB section says full-text search runs on the search module's own collection, kept up to date from track events (not on `tracks`). The features list notes that new uploads become searchable after a short delay. Remove any mention of clearing the library through the API. Verify that the README renders and no longer says search runs on the `tracks` collection. Commit as `docs(search): describe the search read model`.
- [ ] 6.3 Push the branch and open a PR to `main` titled `refactor(search)!: serve search from a read model and drop the delete-all endpoint`. Its body summarizes the change, the removal of `DELETE /api/tracks` (breaking), the spike outcome and the local data wipe. Verify that the `workflow-pr-title` and build checks pass on the PR.

## Workflow follow-up

- Archive the change on the feature branch with `/openspec-archive-change` before merging, so the updated `search` and `track` specs land on `main` with the code.
- When archiving, edit the `track` spec's Purpose in `openspec/specs/track/spec.md` to drop "and clear the library". A delta cannot change a Purpose.
- Squash-merge the PR after review.
