# Design

## Context

See proposal.md for motivation. Requirements are in `specs/track/spec.md` and `specs/search/spec.md`.

Current state relevant to testing:

- All behaviour lives in the `track` module and is served by `TrackController` under `/api/tracks`. The `search` module has no code yet.
- The test infrastructure already exists: `TestContainerDefinitions` provides a MongoDB 7 container through `@ServiceConnection`, and is already used by the two `@SpringBootTest` classes. `src/test/resources/mp3/` has two real mp3 files (3.3 MB and 2.4 MB).
- `spring-boot-starter-test` and `reactor-test` are on the test classpath. There is no WebTestClient usage yet, and nothing in the build declares Spring Boot 4's separate WebTestClient auto-configuration module.
- `GET /api/tracks/{id}/play` rejects requests without a `Range` header (400) but ignores its value.
- `POST /api/tracks` returns an empty body, so a test cannot learn a new track's id from the upload response.
- Text search depends on the MongoDB text index on `title` and `description`, which is created at startup because `auto-index-creation` is enabled.

## Goals / Non-Goals

**Goals:**
- One automated test per spec scenario, named after it, so the specs and tests can be cross-checked by reading them.
- Tests exercise the real HTTP stack and real MongoDB/GridFS, with no mocks, because this is a characterization of observable behaviour.
- No production code change, and no new dependency.

**Non-Goals:**
- Unit tests of internal classes (`DefaultTrackService`, `DbTracks`).
- Testing domain events. They are not observable through the API, and `wire-track-events` will cover them.
- Fixing any known defect found while writing the tests. A defect goes to its follow-up change, and the matching scenario is left out of the spec rather than asserting the wrong behaviour.

## Decisions

### Full application over HTTP, not a Modulith module test
Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `@Import(TestContainerDefinitions.class)`. Several behaviours span modules: for example, the 404 on an unknown track is produced by `ExceptionTranslator` in `infrastructure`.
- *Alternative*: `@ApplicationModuleTest` on `track`. It would bootstrap less, but requires pulling in `infrastructure` and the web layer explicitly. It tests module wiring rather than API behaviour, which `ModularApplicationTests` already covers.

### Bind WebTestClient to the running server manually
Build the client in the test class with `WebTestClient.bindToServer().baseUrl("http://localhost:" + port)`, using the `@LocalServerPort` value. This needs only `spring-test` and WebFlux, which are already present.
- *Alternative*: `@AutoConfigureWebTestClient`. In Spring Boot 4 it lives in a separate test module, so it would add a dependency for no behavioural gain.

### One test class per capability
- `org.tunelad.track.TrackApiTest` covers `specs/track`. It replaces the empty `TrackTest`.
- `org.tunelad.search.TrackSearchApiTest` covers `specs/search`. It lives in the `search` package to mirror the capability and its future module, even though it calls `track` endpoints.

Each test method is named after its scenario, for example `upload_without_optional_metadata`. Both classes share the same Spring context through context caching, so MongoDB starts once.

### Isolate tests by clearing the library through the API
Call `DELETE /api/tracks` in `@BeforeEach`. That keeps the tests black-box, and the delete-all behaviour is itself specified and tested.
- *Alternative*: drop collections with `ReactiveMongoTemplate`. That ties tests to the storage layout (the `tracks` collection and GridFS buckets).
- *Trade-off*: if delete-all breaks, every test fails. The failure points straight at the cause, which is acceptable.

### Resolve ids through the list endpoint
After an upload, find the new track's id by listing tracks and matching on title. Each test uses distinct titles. This works around the empty upload response without specifying it.

### Send `Range: bytes=0-` on play requests
This is what a browser audio element sends, which is what the spec's "as sent by a browser audio element" refers to. It also keeps the play tests valid after `fix-range-streaming`: a full-range request returns the whole file whether the status is 200 or 206. Tests therefore assert a 2xx status, not exactly 200.

### Use the real mp3 fixtures for every upload
The play test compares response bytes with `Knees_Up.mp3` byte for byte. Other tests reuse the same files with custom titles and descriptions. Search tests set titles and descriptions explicitly ("Knees Up", "Mossy Banks", a "Breabach" description), so matching and relevance never depend on file metadata.
- *Alternative*: tiny fake payloads named `*.mp3`. They're faster, but `validate-track-upload` may later inspect file content and break those tests for the wrong reason.

### Design relevance data so the expected order is unambiguous
For "Better match first", one track contains every query word in both title and description, and the other contains a single query word once. MongoDB text scores then differ clearly, so the test doesn't depend on fine score details.

## Risks / Trade-offs

- [Word-variant matching relies on MongoDB's English stemming] → The test asserts only the `knee`/`Knees` pair the spec names. If the text index language changes, this test fails on purpose, signalling a behaviour change.
- [Upload of ~3 MB files per test slows the suite] → Roughly 20 uploads to a local container should take seconds. If it becomes slow, upload once per class for read-only search tests, keeping per-test cleanup for the track tests.
- [The text index may not be ready right after startup] → It is created when the application context starts, before any test request. If tests turn out to be flaky, ensure the index explicitly in a test setup step. That would not change production code.
- [Tests could encode a known defect by accident] → Each test asserts only what its scenario states. Out-of-scope behaviours (a 400 without `Range`, `UNSUPPORTED` format, unenforced title rules) are never asserted.
