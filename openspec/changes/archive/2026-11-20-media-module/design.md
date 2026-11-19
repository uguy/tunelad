# Design

## Context

See proposal.md for the why. The current code that this change splits:

- **Playback.** `TrackController.play` parses the `Range` header (a single range only, otherwise
  the whole file) and answers 200, 206 or 416. It calls `TrackService.execute(PlayTrackCommand)`,
  which looks up the track, then its audio (`Tracks.readMedia`), and errors with
  `NotFoundException` (404 via `ExceptionTranslator`) when either is missing.
- **Storage.** `DbTracks` holds both the `tracks` repository and GridFS:
  - `saveMedia` stores the upload under `filename = track id`;
  - `readMedia` finds that file and returns a `GridFsTrackMedia`, which reads a byte slice by
    querying only the GridFS chunks that cover it (`{bucket}.chunks`, with the bucket name from
    `spring.data.mongodb.gridfs.bucket`, default `fs`).
- **Upload.** `DefaultTrackService.execute(AddTrackCommand)` is `@ReactiveTransactional` and runs
  save metadata → `saveMedia` → publish `NewTrackSaved` through `TransactionalEvents`. GridFS
  writes are not part of the MongoDB transaction.
- **Public API.** `track` exposes `TrackMedia` and `PlayTrackCommand`, used only by its own
  controller.
- **Tests.** About 13 of the 22 tests in `TrackApiTest` cover play and ranges. One of them checks
  the OpenAPI description of the play operation. All of them go through the REST API.
- **Upload requires audio.** The `file` part is required, so every stored track has audio.

## Goals / Non-Goals

**Goals:**

- `media` owns the GridFS data. No other module touches it.
- The dependencies only go one way: `track → media`. `media` never references `track`, and
  `ModularApplicationTests` enforces this.
- Behaviour on the REST API and in the UI is identical, apart from the OpenAPI tag.

**Non-Goals:**

- Making storing the audio and saving the metadata atomic. See Risks.
- Any change to how bytes are read or served.

## Decisions

### D1. `media`'s public API: one storage interface in the base package

`org.tunelad.media.MediaStore` is the only type `media` exposes:

```java
public interface MediaStore {
    /** Stores the audio under the given key; nothing is read until the returned Mono is subscribed. */
    Mono<Void> store(String key, Publisher<DataBuffer> audio);
}
```

It sits in the module's base package, which Modulith exposes by default. That avoids declaring a
`@NamedInterface` for a single type. `track/package-info.java` becomes
`allowedDependencies = { "infrastructure", "media" }`. `media/package-info.java` declares
`allowedDependencies = { "infrastructure" }`, for `NotFoundException`.

The storage implementation (`GridFsMediaStore`) and the readable media type go in
`org.tunelad.media.internal`:

- `TrackMedia` is renamed to `Media`, with the same contract: `length()`, `content(first, last)`.
- `GridFsTrackMedia` is renamed to `GridFsMedia`.

Nothing outside `media` needs to read audio, so the read side stays internal.

**Alternatives considered:**

- Expose reading too, so `track` could keep serving play. Rejected: it would bring back the B
  option we turned down.
- A `media::api` named interface. Rejected as unnecessary for one type. It can be added if the
  API grows.

### D2. The track id is the key, unchanged on disk

`MediaStore.store(key, audio)` writes a GridFS file with `filename = key`, in the same bucket,
exactly as `saveMedia` does today. Play looks up `filename = {id}`.

Media gives the key no meaning: it never asks `track` whether a track exists. Existing local audio
keeps playing, and there is no migration.

**Alternative considered:** a media-assigned id stored on `Track`. Rejected in exploration
(data migration, and either a URL change or a callback from `media` into `track`).

### D3. Upload flow: `track` calls `media` inside its existing chain

`DefaultTrackService.execute(AddTrackCommand)` becomes:

`tracks.save(track) → mediaStore.store(saved.getId(), command.data()) → events.publish(NewTrackSaved)`

- The order and the transaction boundary stay the same as today.
- The `Publisher<DataBuffer>` from the multipart part crosses the module boundary as an argument.
  It is only subscribed when `media` stores it, so the upload keeps streaming without buffering.
- `Tracks.saveMedia` and `Tracks.readMedia` are removed. `DbTracks` keeps only the `tracks`
  repository.

### D4. Play endpoint: `MediaController` in `media`, same contract

`org.tunelad.media.web.MediaController` takes over `GET /api/tracks/{id}/play`. The
`singleRange`, `wholeFile` and `partial` logic and the OpenAPI annotations move unchanged.

- It reads through the internal store: `GridFsMediaStore.find(key)` returns `Mono<Media>`, empty
  when there is no file. When it is empty, the controller errors with `NotFoundException`, which
  still maps to 404.
- "No track has that id" in the spec is observably the same as "no audio stored under that id",
  because every upload stores audio (see Context).
- `PlayTrackCommand`, `TrackService.execute(PlayTrackCommand)` and `TrackMedia` are deleted from
  `track`. `TrackController` loses `play` and its Range helpers.

**OpenAPI changes:** the `/api/tracks/{id}/play` operation moves from the `Tracks` tag to a new
`Media` tag ("Stored audio and playback"). Its summary, description, `Range` parameter, 200, 206,
404 and 416 responses and their headers are unchanged. No schema component changes, and no other
operation changes.

**Reactive:** the controller and the store stay fully reactive, as today. No new blocking call is
introduced.

### D5. Tests

- **`MediaApiTest`** (`org.tunelad.media`, extends `AbstractApiTest`): the play and range tests
  move here from `TrackApiTest` unchanged, still one test per spec scenario and named after it.
  They set up data by uploading through `POST /api/tracks`, black-box as before. The OpenAPI test
  also asserts that the operation is tagged `Media`.
- **`TrackApiTest`** keeps the upload, list, get and identifier tests, and the 405 test for the
  removed delete-all. Its play helpers (`kneesUpBytes`, `uploadKneesUp`) move with the tests that
  use them. `play(...)` stays in `AbstractApiTest`.
- **Module test:** an `@ApplicationModuleTest` for `media` (standalone, with Testcontainers Mongo)
  stores bytes through `MediaStore` and reads them back with `find(key)`. It also reads a slice
  across a chunk boundary. That shows `media` boots and works without `track`.
- `ModularApplicationTests` must pass. It fails if `media` ever references `track`.

## Risks / Trade-offs

- **[Orphaned audio]** If publishing `NewTrackSaved` fails after the audio is stored, the
  metadata rolls back but the GridFS file stays. Play would then serve audio for an id that has no
  track. → The same as today (GridFS was already outside the transaction). Such ids are random
  UUIDs that were never returned to any client, so this cannot be observed. Cleaning up orphans is
  out of scope.
- **[`media` cannot validate keys]** By design, `media` does not know whether a key belongs to a
  track. → Only `track` stores audio, and only under ids of tracks it just saved. The REST API
  exposes no other way to store audio.
- **[A large move hides behaviour changes]** Moving about 13 tests and the controller could hide
  an accidental change. → The tests and requirements move verbatim (the spec move is checked with
  a diff). The full test suite must pass before and after with the same scenario names.
- **[Spring Modulith test selection]** `spring-modulith-junit` only runs tests for modules it
  thinks changed. → Run the verification with a full test run, as in the previous change
  (`spring.modulith.test.file-modification-detector=reference-commit` with
  `on-no-changes=execute-all`), or once all commits are in place.

## Migration Plan

None. No data, URL or configuration changes. Deploy and roll back as usual.
