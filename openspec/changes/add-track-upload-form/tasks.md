# Tasks

## 1. Branch

- [x] 1.1 Update `main` (`git switch main && git pull`) and create `feat/track-upload-form` from it. Commit this change's `openspec/changes/add-track-upload-form/` files as `docs(openspec): propose add-track-upload-form change`. Verify `git status` shows the new branch with a clean tree

## 2. Content type per audio file

Commit: `feat(media): serve each audio file with its own content type`

- [x] 2.1 Change `MediaStore.store` to `store(String key, Publisher<DataBuffer> audio, String contentType)` and implement it in `GridFsMediaStore` with `ReactiveGridFsOperations.store(content, filename, contentType)`. Add `contentType()` to `Media`, read from the GridFS metadata with a fallback to `audio/mpeg` when absent (design.md decision 5). For now, pass `audio/mpeg` from `DefaultTrackService`. Verify `ModularApplicationTests` passes
- [x] 2.2 Use `media.contentType()` in `MediaController` for 200 and 206 responses, and list `audio/mpeg`, `audio/mp4`, `audio/ogg`, `audio/flac` and `audio/wav` in its `produces` and OpenAPI response content. Verify `/v3/api-docs` shows them
- [x] 2.3 Add `GridFsMediaStoreTest` cases: the stored content type is returned by `find`, and a file stored without one (written directly through `ReactiveGridFsOperations` with no content type) reports `audio/mpeg`. Add a `MediaApiTest` test for "Audio stored before formats were recorded" (store audio without a content type under a track's id). Verify the existing `MediaApiTest` range tests still pass with `./mvnw verify -DskipITs`

## 3. Upload API: 201, formats, validation, size limit

Commit: `feat(track)!: return the created track and accept more audio formats`

- [x] 3.1 Rework `TrackFormat` to `MP3, M4A, OGG, FLAC, WAV` with extensions and MIME types, and keep `UNSUPPORTED` for reading old documents only. `forFile` matches case-insensitively and returns an `Optional` (design.md decision 1). Add a `TrackFormatTest` unit test covering each extension, upper case, no extension and `.txt`
- [x] 3.2 In `TrackController.create`, refuse a blank `title` or `description` with 400 and an unknown format with 415 before building the command (decision 2). Return `Mono<ResponseEntity<TrackDTO>>` built with `ResponseEntity.created(...)`, after changing `TrackService.execute(AddTrackCommand)` to return `Mono<TrackDTO>`. Pass `format.mimeType()` to `MediaStore.store`. Update the OpenAPI `@ApiResponses` (201 with `TrackDTO` schema and `Location` header, 400, 401, 403, 413, 415) and verify `/v3/api-docs` shows them
- [x] 3.3 Set `spring.webflux.multipart.max-disk-usage-per-part: 100MB` in `application.yml`. If the framework doesn't already answer 413, map the limit exception to 413 in `ExceptionTranslator` (decision 3)
- [x] 3.4 Add test fixtures `tune.m4a`, `tune.ogg`, `tune.flac`, `tune.wav` (small real files) under `src/test/resources`, and an `AbstractApiTest` upload variant that takes a `Resource`, so tests can stream a generated temporary file. Make `uploadOk` expect 201
- [x] 3.5 Add `TrackApiTest` tests, one per scenario, for "Upload a track" (201 body, `Location`, fetch from location, missing or blank parts), "Accepted audio formats" and "Upload size limit" (temporary files of 104,857,600 and 104,857,601 bytes). Add a `MediaApiTest` test for "Play each supported format". Update the existing tests that expected 200 from an upload. Verify `./mvnw verify -DskipITs` passes

## 4. Nothing left behind after a failed upload

Commit: `fix(track): leave no track or audio behind when an upload fails`

- [x] 4.1 Rework `DefaultTrackService.execute(AddTrackCommand)` as in design.md decision 4: generate the id up front, store the audio outside the transaction, then save the track and publish `NewTrackSaved` through a `TransactionalOperator` on the reactive transaction manager. Delete the audio by key on error (then rethrow) and on cancel. Verify the existing `TrackEventsTest` still passes (one `NewTrackSaved` per upload)
- [x] 4.2 Add tests for "A failed upload leaves nothing behind". "Storing the audio fails" uses a `MediaStore` test double that errors after the first buffer: no track listed, nothing in `fs.files`/`fs.chunks` for the key, no `NewTrackSaved`. "Client aborts during the upload" uses a raw HTTP client that sends part of a multipart body and closes the connection: no track, no GridFS file, and search finds nothing after 5 seconds. Verify they pass

## 5. Upload page in the UI

Commit: `feat(ui): add an upload page with progress, cancel and retry`

- [x] 5.1 In `app/src/routes/tracks/Tracks.ts`, add the `TrackUpload` type, the `SUPPORTED_EXTENSIONS` and `MAX_FILE_SIZE` constants (commented with their server counterparts), `uploadErrorMessage(status)`, `checkFile(file)` and `TrackService.upload(upload, { onProgress, signal })` using `XMLHttpRequest` with the CSRF header (design.md decision 7). Add vitest tests for `checkFile` (size limit boundary, each extension, upper case, unsupported), `uploadErrorMessage` (413, 415, 400, 401, 403, 500, 0) and the progress fraction. Verify `npm run test:unit -- --run` passes
- [x] 5.2 Add `app/src/routes/tracks/upload/+page.svelte` (decision 8): the form with `required` title, description and file (`accept` set to the five formats), optional artist and album, progress bar, disabled submit and "Cancel" while uploading, a `role="alert"` message after a failure or cancel with all input kept, and the "Log in to upload" link for anonymous visitors
- [x] 5.3 On success, `goto(resolve('/tracks'), { state: { uploaded: track } })`. Declare `App.PageState` in `app.d.ts`, and in `tracks/+page.svelte` put `page.state.uploaded` at the top of the list when its id isn't there yet (decision 9). Add the "Upload" / "Log in to upload" link on the tracks page
- [x] 5.4 Add `app/e2e/track-upload.test.js` (API mocked with `page.route`, like `track-ownership.test.js`) with one test per scenario of "Upload link on the tracks page", "Upload a track from the UI" (assert the multipart parts and the `X-XSRF-TOKEN` header, and that the new track is listed while search still returns the old list), "Upload progress and cancel in the UI" (hold the response back, check the progress bar, disabled submit, Cancel, and input kept) and "Failed upload handling in the UI" (one case per status and a network failure via `route.abort()`, retry, files refused before sending). Run `npm run format` and `npm run lint`, and verify `npm run test:e2e` passes

## 6. Finish

- [x] 6.1 In README.adoc's Features, change the upload line to "Upload a track from the UI or the API (mp3, m4a, ogg, flac or wav, up to 100 MB; requires login or a bearer token), with progress, cancel and retry", and update the upload curl recipe to show the 201 response. Verify the recipe works against the running app
- [x] 6.2 Run `./mvnw clean verify` and verify it passes, including `ModularApplicationTests`, vitest and the Playwright e2e tests
- [x] 6.3 Smoke-test with `docker compose up -d` and the built app: as alice, upload a flac file from `/tracks/upload` and see it listed at once and playing (content type `audio/flac` in devtools). Cancel a large upload halfway and check Mongo Express shows no new `fs.files` entry. Try a `.txt` file and a file over 100 MB, and see the messages without a request being sent
- [ ] 6.4 Push `feat/track-upload-form` and open a PR to `main` titled `feat(track)!: add an upload form with progress and more audio formats`, calling out the 200 → 201 change in the description

## Workflow follow-up

- Archive the change with `/openspec-archive-change` on the feature branch before merging, so specs land in `main` with the code. When archiving, update the `media` spec's Purpose if needed (it still says the audio is mp3 only by implication).
