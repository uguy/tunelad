# Design

## Context

See proposal.md for the why. The current state that shapes the design:

- Search is served by `TrackController` → `TrackService.searchTracks` → `DbTracks.searchTracks`.
  That is a MongoDB `$text` query sorted by score on the `tracks` collection, which is indexed
  through `@TextIndexed` on `Track.title` and `Track.description`. A blank query falls back to
  `findAll()`.
- `track` already publishes `NewTrackSaved(TrackDTO)` (after the audio is stored) and
  `AllTracksDeleted` through a plain `ApplicationEventPublisher`. The only listener,
  `DefaultTrackEventHandler`, logs them. `AllTracksDeleted` comes from `DELETE /api/tracks`, which
  only the API tests call (`AbstractApiTest` before each test, plus one scenario test). The UI and the
  Playwright tests never call it; the Playwright tests mock the API.
- No transaction manager bean exists. Spring Boot does not configure one for MongoDB, so the
  `@Transactional` on `DefaultTrackService` does nothing today. That probably explains the comment
  in `DefaultTrackEventHandler` about transactional listeners not firing.
- MongoDB transactions need a replica set. Testcontainers' `MongoDBContainer` starts a
  single-node replica set. The `mongo` service in `docker-compose.yaml` is a standalone server
  with root authentication, so it does not support transactions.
- `AbstractApiTest` deletes all tracks through `DELETE /api/tracks` before every test, then tests
  upload and query straight away.
- The frontend calls `tracks/search?q=` with plain `fetch` and untyped JSON (no generated OpenAPI
  client), so renaming a schema component does not affect it.
- Versions: Spring Boot 4.1 (Spring Framework 7), Spring Modulith 2.1.

## Goals / Non-Goals

**Goals:**

- A search read model owned by the `search` module, updated only from `track` events. `search`
  never reads track's collections.
- Event delivery with `@ApplicationModuleListener` and the Event Publication Registry. The
  registry's limits in a reactive app are found out by a spike first and written down.
- No blocking call on a WebFlux request thread. Any blocking call is named, and the thread it runs
  on is stated.

**Non-Goals:**

- Ordering guarantees between different events. See Risks.
- Automatically resubmitting incomplete publications on restart.
- Any read model other than search.

## Decisions

### D0. Remove delete-all before anything else

`DELETE /api/tracks`, `DeleteAllTrackCommand`, `TrackService.execute(DeleteAllTrackCommand)`,
`Tracks.deleteAll()`, the `AllTracksDeleted` event and its `on(...)` handlers are deleted first, in
their own commit. After that, `track` publishes only `NewTrackSaved`. Everything below then deals
with a single event type, whose writes are idempotent by id. This avoids the ordering problem
between "delete everything" and "add track", and the existing race in how `AllTracksDeleted` is
published (`deleteAll().and(Mono.fromSupplier(publish))` publishes at the same time as the delete,
whether or not it succeeds).

**Test reset.** `AbstractApiTest` resets the database by deleting the documents of every
non-system collection in the test database, through `ReactiveMongoTemplate`, and blocks in
`@BeforeEach`, which is fine in test code. This choice:

- does not name any module's collections, so it also covers GridFS, `search_tracks` and the
  registry's publication collection;
- keeps indexes, unlike dropping the database. Indexes are created only at startup
  (`auto-index-creation`), so dropping them would break the text search for the rest of the run;
- is synchronous, so no event delivered late can wipe the next test's data.

**OpenAPI:** the `DELETE` operation on `/api/tracks` (summary "Delete tracks") disappears from the
description. The other operations under the `Tracks` tag are unchanged.

**Alternatives considered:**

- A test-only endpoint or Spring profile. Rejected: it keeps production code that exists only for
  tests.
- Dropping the database between tests. Rejected because it loses the indexes.
- Restarting the Testcontainers container. Rejected as too slow.

### D1. Spike first, with a fixed decision rule

The first task group is a throwaway spike on the feature branch. It answers four questions with a
test against Testcontainers Mongo:

1. With a `ReactiveMongoTransactionManager` in place, does an event published inside a reactive
   `@Transactional` method reach an `@ApplicationModuleListener` after the commit, and not reach
   it on rollback?
2. Is the registry's publication record written with a blocking driver call, and on which thread?
3. Is that record written atomically with the track documents, in the same transaction?
4. Do the listeners run asynchronously without extra configuration?

**Decision rule:**

- If (1) works and any blocking in (2) can be moved off the event loop (D4), go with option 3:
  `@ApplicationModuleListener` with the registry.
- If (1) cannot be made to work, use the fallback (D8).
- (3) does not decide anything by itself. Its answer is recorded under Risks.

The answers are added to this document before the next task group starts.

**Alternative considered:** commit straight to option 3 without a spike. Rejected, because (1) has
already failed once in this codebase (the comment in `DefaultTrackEventHandler`), and (2) could
break the "stay reactive" rule.

### D2. Publish events with the reactive transactional publisher

`DefaultTrackService` replaces `ApplicationEventPublisher.publishEvent(...)` with Spring's
`org.springframework.transaction.reactive.TransactionalEventPublisher` (Spring Framework 6.1+). It
returns a `Mono<Void>` and publishes the event with the reactive transaction attached. That is what
`@TransactionalEventListener`, and therefore `@ApplicationModuleListener`, needs in order to wait
until the commit. The publish becomes part of the reactive chain:
`save → saveMedia → publish(NewTrackSaved)`.

**Alternative considered:** keep `ApplicationEventPublisher` inside a `flatMap`. It cannot see the
reactive transaction, which is held in the Reactor context rather than a thread-local, so
after-commit listeners never fire.

### D3. Transaction manager lives in `infrastructure`

A `ReactiveMongoTransactionManager` bean is added to a configuration class in the `infrastructure`
module, which `track` already depends on. With it, the existing `@Transactional` annotations on
`DefaultTrackService` take effect. GridFS writes are not covered by it. See Risks.

### D4. Keep registry writes off the event loop

If the spike shows that the Mongo registry stores publications through the blocking
`MongoTemplate` when the event is published, the publish step is moved to
`Schedulers.boundedElastic()`. For example, `publishOn(boundedElastic)` goes right before the
publish, or the publish is wrapped in `Mono.fromRunnable(...).subscribeOn(boundedElastic())`,
whichever the spike shows keeps the transaction context. This blocking call is named here on
purpose. It is the only blocking call in the request path, and it runs on a worker pool meant for
blocking work, not on a Netty thread.

### D5. Read model: a separate collection with its own text index

`search` owns a `search_tracks` collection of documents with the fields
`id, title, description, artist, album, format`.

- `id` is the track id, so writing the same track twice just replaces it. This matters because the
  registry delivers at least once.
- A compound text index covers `title` and `description`, the same fields and the same default
  language as today. All existing matching and ranking scenarios therefore keep passing without
  changes.
- The query is the same `TextQuery ... sortByScore()`, and a blank query returns all documents.
- `@TextIndexed` is removed from `Track`, so the write model no longer carries a search index.

The document is mapped from the `TrackDTO` carried by `NewTrackSaved`. That keeps `search`
depending only on `track` (for the `TrackDTO` type) and `track::event`. `track::command` is removed
from `search/package-info.java`.

**Alternative considered:** a MongoDB view or `$lookup` over `tracks`. Rejected: that would mean
reading track's storage directly, which breaks module boundaries and defeats the point of CQRS.

### D6. Listener: `@ApplicationModuleListener` in `search`, blocking on its own thread

`SearchProjection` (search module, internal) has one method, `on(NewTrackSaved)`, which upserts the
document by track id. It is `void` and calls the reactive repository with `.block()`. This is
deliberate. An
`@ApplicationModuleListener` is `@Async`, so it runs on a task executor thread, never on a Netty
thread. The registry also marks a publication complete when the method returns, so the write must
have finished by then. Returning an unsubscribed `Mono` would mark the event complete before
anything is written.

The existing `DefaultTrackEventHandler` stays as a logging listener inside `track`, minus its
`AllTracksDeleted` method (D0).

### D7. REST: the endpoint moves to a controller in `search`

`SearchController` in `org.tunelad.search.web` maps `GET /api/tracks/search?q=`. The path,
parameter, status codes and JSON field names stay the same. The search endpoint is removed from
`TrackController`, and `searchTracks` is removed from `TrackService` and `Tracks`.

**OpenAPI changes:**

- The operation moves from the `Tracks` tag to a new `Search` tag ("Search tracks by words in
  title or description").
- It gains a summary, a description stating that results are eventually consistent, and a
  documented `q` parameter.
- The response schema becomes a search-owned component (`TrackSearchResult`) with the same
  properties as `Track`. The path, the parameter and the JSON shape on the wire do not change.

### D8. Fallback if the spike fails

The publisher stays plain, with no transactional dependency. The listener becomes an
`@EventListener` returning `Mono<Void>`. Spring subscribes to it without blocking, and delivery is
fire-and-forget. D0, the read model, the controller, the specs and the tests are the same. Only D2's
transactional publisher, D3 and D4 are dropped. The reason is recorded in this document.

### D9. Tests

- **Black-box API tests** (`TrackSearchApiTest`, one test per spec scenario) stay. After uploading,
  they wait for the projection with Awaitility (version managed by Boot), up to the 5 seconds the
  spec allows.
- **Reset before each test:** the direct database reset (D0) replaces the HTTP `DELETE`. The
  "Clear a populated library" test in `TrackApiTest` is removed together with its requirement.
- **New search scenarios:** "New track found after a short delay" and "Searchable track has the
  track list fields" each get a test.
- **Module test:** an `@ApplicationModuleTest` for `search` uses the Modulith `Scenario` API
  (`spring-modulith-starter-test`). It publishes `NewTrackSaved` and waits for the state change in
  the read model. That shows the projection works with only its own module bootstrapped, and that
  delivering the same event twice leaves one document.
- `ModularApplicationTests` must pass with the smaller `allowedDependencies`.

## Risks / Trade-offs

- **[Removing an endpoint breaks clients]** `DELETE /api/tracks` disappears. → Nothing in the UI,
  the e2e tests or the README uses it (checked). The PR title carries `!`, and the `track` spec
  delta records the reason and the migration.
- **[Event ordering will matter again]** Today the only event is `NewTrackSaved`, and its upserts
  are idempotent by id, so delivery order does not matter. → A future update or delete event will
  bring the ordering question back. That change will need a version or timestamp on events.
- **[Test reset clears everything]** The direct reset also empties the registry's publication
  collection while an async listener from the previous test might still be running. → Each test
  awaits the projection state it checks. A late write from a previous test can only add a document
  for a track that test uploaded, and tests assert on their own titles.
- **[Registry write may not be atomic with the track write]** The registry may store publications
  through the blocking driver and the synchronous transaction infrastructure, outside the reactive
  transaction (spike question 3). A rolled-back upload could then leave an incomplete publication
  behind. Resubmitting it would index a track that does not exist. → Leave automatic resubmission
  on restart turned off (the Modulith default) and record what the spike finds.
- **[GridFS is outside the transaction]** `ReactiveGridFsOperations` writes are not part of the
  Mongo transaction. A failure after the audio is stored can leave orphaned audio chunks. This is
  already the case today; the change does not make it worse.
- **[Local Mongo cannot do transactions]** → `docker-compose.yaml` runs `mongo` as a single-node
  replica set. Authentication with a replica set needs a key file, generated by the container's
  entrypoint, and a one-off `rs.initiate()` in a healthcheck or init script. The application's
  connection settings may need `directConnection=true`.
- **[Search lags behind uploads]** A search right after an upload can miss the new track. This
  behaviour is in the spec (5-second limit). The UI does not search right after an upload, so
  users are not affected.
- **[Blocking in listeners]** `.block()` in `SearchProjection` ties up an executor thread for each
  event. That is acceptable at this scale, and it never runs on a Netty thread (D6).

## Migration Plan

1. Run `docker compose down -v`, then bring the stack up again with the replica-set
   configuration. This wipes local tracks and audio, and with them the old text index on `tracks`.
   Without the wipe, Mongo would keep that index even after `@TextIndexed` is removed.
2. Start the app. The read-model collection, its text index and the registry's collection are
   created on first use (`auto-index-creation: true`).
3. Upload tracks again (for example with the disabled `ImporterTest`).

To clear a local library later, now that `DELETE /api/tracks` is gone, wipe the data again with
`docker compose down -v`.

Rollback: revert the PR and wipe local data again. The `search_tracks` collection and the
publication collection are left over but harmless.

## Open Questions

- The exact collection name the Modulith Mongo registry uses for publications, and whether it can
  be renamed. It is cosmetic and gets settled during the spike.
- Whether the Modulith actuator endpoint shows incomplete publications usefully, or whether a log
  line is enough. Cosmetic.
