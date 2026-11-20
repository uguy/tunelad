# Design

## Context

What the code does today, as far as it matters for this change:

- `TrackController.create` takes `@RequestPart` values and a `FilePart`, and returns `Mono<Void>`, so the response is
  200 with no body. `TrackFormat.forFile` maps the extension to `MP3` or `UNSUPPORTED`, and `UNSUPPORTED` tracks are
  stored like any other.
- `DefaultTrackService.execute(AddTrackCommand)` is `@ReactiveTransactional`. It saves the `Track`, then streams the
  audio into GridFS through `MediaStore.store(key, audio)`, keyed by the track id, then publishes `NewTrackSaved`
  inside the transaction. As far as we know, GridFS writes don't take part in the Mongo transaction. That needs
  checking, so the design doesn't rely on it either way.
- `media` depends only on `infrastructure`. `MediaController` always answers `audio/mpeg`, because the store keeps no
  content type.
- No multipart limit is configured. `spring.webflux.multipart.max-disk-usage-per-part` defaults to unlimited.
- The UI's `api.request` always sends JSON. The tracks page is prerendered with `ssr = false`, reads the logged-in
  user from `session`, and lists tracks from `/api/tracks/search`, which catches up with new tracks a few seconds
  late. Edits avoid that delay by putting the server's answer straight into the list.

## Goals / Non-Goals

**Goals:**

- Check the request before writing anything, so a refused upload (400, 413, 415) costs no database writes.
- Make cleanup after a failure explicit, so it doesn't depend on whether GridFS writes join the transaction.
- Keep `media` unaware of tracks: it learns a content type, not a `TrackFormat`.
- Use framework features where they fit: `ResponseEntity.created`, the WebFlux multipart limit, and SvelteKit's
  `goto(..., { state })` and `page.state`.

**Non-Goals:**

- Checking the file's content against its extension.
- Backfilling content types for audio stored before this change. It falls back to `audio/mpeg` at read time.
- A generic upload component reusable elsewhere in the UI.

## Decisions

### 1. Formats are identified by extension; `TrackFormat` carries the MIME type

`TrackFormat` gets `MP3, M4A, OGG, FLAC, WAV`, each with its extensions and MIME type (`audio/mpeg`, `audio/mp4`,
`audio/ogg`, `audio/flac`, `audio/wav`). The extension is matched case-insensitively. `forFile` returns an `Optional`,
and an empty result means the controller answers 415 with
`ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE)`. `UNSUPPORTED` stays in the enum, only so that tracks
already stored with it can still be read. Nothing new is stored with it.

*Alternatives:* trusting the part's `Content-Type` header (browsers disagree: FLAC arrives as `audio/flac`,
`audio/x-flac` or `application/octet-stream`), or sniffing magic bytes (more reliable, but out of scope). Using the
extension is predictable and matches what the file picker's `accept` attribute filters on.

### 2. Validation happens in the controller, before the command exists

The controller refuses a blank `title` or `description` with 400 (`ServerWebInputException`), and an unknown format
with 415, before it builds an `AddTrackCommand`. Missing parts already answer 400 through `@RequestPart`. Bean
Validation on `@RequestPart String` values would work too, but would add little for two checks.

### 3. Size limit through the WebFlux multipart reader

Set `spring.webflux.multipart.max-disk-usage-per-part: 100MB` (and keep the default `max-in-memory-size`). By default,
the `DefaultPartHttpMessageReader` stores parts in temporary files before the handler runs. An oversized part fails
while the request is being parsed, before the controller, so nothing is written. The exception it raises
(`DataBufferLimitException`, possibly wrapped) is mapped to 413 in `ExceptionTranslator` if the framework doesn't
already answer 413. The first API test settles which applies. A file of exactly 104,857,600 bytes must still pass,
which the boundary tests check. Tests stream a temporary file of that size rather than holding it in memory.

*Alternative:* counting bytes while streaming `FilePart.content()` into GridFS. That would reject the file after the
writes had started, which is exactly what goal 1 avoids.

### 4. Audio first, outside the transaction; then the track, in it; cleanup on error and on cancel

The order changes:

```
  id = new ObjectId()
  mediaStore.store(id, audio, format.mimeType())         -- no transaction
    then  [ tracks.save(track with id) ; events.publish(NewTrackSaved) ]   -- TransactionalOperator
    on error or cancel at any step:  mediaStore.delete(id)   (then rethrow the error)
  returns TrackDTO
```

- The track id is generated up front, so the audio can be stored under its key before the track exists.
- `execute(AddTrackCommand)` drops the class-level `@ReactiveTransactional` for a `TransactionalOperator` around the
  save and the publish only. That keeps the GridFS write out of the transaction's time limit, and the Event
  Publication Registry entry stays in the same transaction as the track, as today. The operator uses the same reactive
  transaction manager that `ReactiveTransactional.MANAGER` names.
- Cleanup: `onErrorResume(e -> mediaStore.delete(id).then(Mono.error(e)))` for errors, and `doOnCancel` subscribing
  to `mediaStore.delete(id)` for a client that goes away. On cancel nobody is waiting for the result, so the delete is
  started without being awaited (its failure is logged). The transaction rolls back on cancel and on error, so the
  track and the event never become visible. `MediaStore.delete` already completes normally when nothing is stored.
- The service returns `Mono<TrackDTO>`, and the controller answers
  `ResponseEntity.created(URI.create("/api/tracks/" + id)).body(dto)`.

*Alternatives:* keeping today's order (track first) and only adding cleanup. That works, but leaves a long GridFS write
inside the transaction and makes cleanup depend on how GridFS interacts with transactions. Using `Mono.usingWhen` with
the id as the resource gives one place for commit, error and cancel handling. That's equally valid. The
`onErrorResume`/`doOnCancel` form is chosen because it's easier to read for a single resource.

### 5. `media` stores the content type with the file

`MediaStore.store(String key, Publisher<DataBuffer> audio, String contentType)` replaces the two-argument method, and
`GridFsMediaStore` uses `ReactiveGridFsOperations.store(content, filename, contentType)`, which writes it to the
file's metadata. `Media` gains `contentType()`, read from the metadata and falling back to `audio/mpeg` when it's
absent (audio stored before this change). `MediaController` uses it for both 200 and 206 responses. Its OpenAPI
`produces` lists the five audio types.

*Alternative:* `media` asks `track` for the format. That would need a dependency from `media` to `track`, the reverse
of how the two modules depend on each other today.

### 6. REST API and OpenAPI

`POST /api/tracks` documents: 201 with `TrackDTO` schema and a `Location` header, 400 (missing *or blank* part), 401,
403 (CSRF), 413 and 415. `TrackDTO.format` and the search result's format show the new enum values. The change is
breaking for clients that expect 200, which the PR title's `!` marks. Nothing in this repository depends on the 200,
apart from tests that use `is2xxSuccessful()` or `isOk()`. Those are updated.

### 7. UI: `XMLHttpRequest` behind a promise with an `AbortSignal`

`TrackService.upload(upload: TrackUpload, { onProgress, signal }): Promise<Track>` sends a `FormData` with
`XMLHttpRequest`, because `fetch` can't report upload progress. It reports `upload.onprogress` as a fraction, adds the
`X-XSRF-TOKEN` header from `api.csrfToken()`, and calls `xhr.abort()` when the signal aborts. It resolves with the
parsed 201 body. It rejects with a `TrackUploadError` that carries the status (0 for a network error) and the user
message for that case, or with an `AbortError` on cancel. The messages come from one function shared with the
browser-side checks, so the same refusal reads the same either way. The supported extensions and the 100 MB limit are
constants next to it, with a comment pointing at their server counterparts.

*Alternative:* `fetch` with a `ReadableStream` request body to count bytes. It needs `duplex: 'half'`, has patchy
browser support, and isn't supported over HTTP/1.1 in Chromium.

### 8. UI: the page and keeping input after a failure

`app/src/routes/tracks/upload/+page.svelte` holds the form state in `$state` runes. It has
`status: 'editing' | 'uploading' | 'cancelled' | 'failed'`, a progress number, and an error message. The chosen
`File` is kept in state, and the file input stays bound to it, so a failure or cancel never clears what the user typed
or picked, and submitting again retries with the same values. While uploading, the submit button is disabled and a
"Cancel" button aborts the `AbortController`. Native `required` attributes handle the empty-field checks. The size and
extension checks run on submit, before `upload` is called. Anonymous visitors (`session.account` is null) see the
"Log in to upload" link instead of the form.

### 9. UI: showing the new track at once with SvelteKit page state

After a 201, the page calls `goto(resolve('/tracks'), { state: { uploaded: track } })`. The tracks page reads
`page.state.uploaded` and puts it at the top of the loaded list when the list doesn't already have that id. It's
typed through `App.PageState` in `app.d.ts`. The state is gone after a reload, and by then search has caught up.

*Alternatives:* a shared rune module holding recent uploads (it lasts the whole session and needs clearing), or a query
parameter with the id plus an extra `GET /api/tracks/{id}` (an extra request, and the URL gets cluttered).
`page.state` is the SvelteKit feature made for passing this kind of short-lived navigation data.

## Risks / Trade-offs

- [GridFS cleanup on cancel races with a write still in progress] → The delete is by filename, after the store's
  publisher has been cancelled. An API test aborts mid-upload and checks that no `fs.files` or `fs.chunks` remain for
  the key. If the race shows up, the delete is retried once after the store publisher terminates.
- [Spring's default reader buffers the whole part before the handler, so cancels mostly happen before any write] →
  Cleanup is still needed when GridFS fails, or when the client leaves during the write. A failing-store test (a
  `MediaStore` test double that errors after the first buffer) covers the error path.
- [The 413 mapping depends on how the framework surfaces the limit] → Decision 3's test settles it. `ExceptionTranslator`
  maps it only if needed.
- [Breaking 200 → 201] → Flagged in the PR title. Scripts using `curl -f` still treat 201 as success.
- [Playwright can't easily produce real upload progress against a mocked route] → The fraction calculation is a
  pure function covered by a vitest test. E2E tests hold the mocked response back, to check the progress bar is shown,
  the submit button is disabled and Cancel works.
- [Temporary disk use: up to 100 MB per concurrent upload in the temp directory] → Acceptable for a playground. The
  reader deletes temp files after the request.

## Migration Plan

No data migration. Tracks stored as `MP3` or `UNSUPPORTED` stay readable, and their audio is served as `audio/mpeg`.
Rolling back is a plain revert: files uploaded in the new formats keep their content type metadata, and the old code
ignores it.
