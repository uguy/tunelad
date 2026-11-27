# Tasks

## 1. Branch

- [x] 1.1 Create the branch `feat/wma-support` from an up-to-date `main`; verify with `git branch --show-current`.

## 2. WMA as an accepted format

Commit: `feat(track): accept wma uploads`

- [x] 2.1 Add `WMA("audio/x-ms-wma", "wma")` to `TrackFormat`; verify a unit test finds it for `tune.wma` and `TUNE.WMA` and still refuses `notes.txt`.
- [x] 2.2 Add a `TestAudio.wma()` helper in `src/test/java/org/tunelad/media/TestAudio.java` that makes a short WMA by piping `silenceThenTone()` through `ffmpeg -c:a wmav2`, and a `TestAudio.fakeWma()` of noise; verify the helper's output starts with the ASF header GUID `30 26 B2 75`. No third-party recording is committed, and the untracked Dervish `.wma` files stay out of git.
- [x] 2.3 Update the 415 text in `TrackController` to list wma; verify the OpenAPI description (`/v3/api-docs`) shows it.
- [x] 2.4 Add the upload test "Upload each supported format" for `.wma` and `.WMA` in the track API tests; verify they pass with `./mvnw test -Dtest=<TrackApi test class>`.
- [x] 2.5 Add `wma` to `SUPPORTED_EXTENSIONS` and `audio/x-ms-wma` to `ACCEPTED_FILES` in `app/src/routes/tracks/Tracks.ts`, and a case in `Tracks.test.ts` for `checkFile`; verify with `cd app && bun run test`.

## 3. Rendition storage and state

Commit: `feat(media): store a playable rendition state for audio browsers cannot play`

- [x] 3.1 Add a Liquibase changeset for `media_renditions` to `src/main/resources/db/changelog/media.sql`, with a rollback; verify the app starts against Testcontainers PostgreSQL.
- [x] 3.2 Add the `Playable` rule and a `Renditions` repository (find, save pending, ensure pending, complete, pending keys) in `media::internal`; verify with a repository test.
- [x] 3.3 In `ChunkedMediaStore`, save a `PENDING` rendition row in the transaction that saves the pending waveform when the content type needs one, exclude `*:play` keys from `keys()`, and delete `<key>:play` in `delete(key)`; verify in `ChunkedMediaStoreTest` that deleting a key removes the rendition, its state and its chunks, and that `keys()` omits renditions.
- [x] 3.4 Add `media::event.AudioPrepared(String key)` with its package documentation; verify `ModularApplicationTests` still passes.

## 4. Making the rendition

Commit: `feat(media): convert wma to an aac rendition with ffmpeg`

- [x] 4.1 Add `tunelad.media.ffmpeg.transcode-timeout` (default `5m`) to `FfmpegProperties`; verify the binding in a properties test.
- [x] 4.2 Add `RenditionMaker`, which streams the audio to ffmpeg, writes an `.m4a` temporary file with `+faststart`, and returns its content as `Flux<DataBuffer>` while deleting the file at the end; all blocking work on `Schedulers.boundedElastic()`. Verify with a test, assuming ffmpeg is present, that a generated WMA gives audio ffprobe reports as AAC with a duration near 4 s, and that noise fails with `UndecodableAudioException` and leaves no temporary file.
- [x] 4.3 Add `RenditionComputer`, an `@ApplicationModuleListener` on `AudioStored` that stores `<key>:play` as `audio/mp4`, saves `READY` or `FAILED`, leaves `PENDING` with a single warning when ffmpeg is missing, and publishes `AudioPrepared` in a transaction of its own only when a row changed; verify with tests for ready, failed, ffmpeg-missing, deleted-meanwhile and "no event for browser formats".
- [x] 4.4 Add `RenditionBackfill` on `ApplicationReadyEvent`, one at a time and in the background; verify that a row left `PENDING` becomes `READY` when the backfill runs.

## 5. Playing the rendition

Commit: `feat(media): play the rendition and expose the audio state`

- [x] 5.1 In `MediaController.play`, serve `<key>:play` when the rendition is `READY`, answer 503 with `Retry-After` when `PENDING` and 422 when `FAILED`, and keep the original path untouched for audio with no rendition row; document both responses in OpenAPI; verify with API tests for "Play a WMA track" (with and without `Range: bytes=0-`, `Content-Length` of the rendition), "Play while the rendition is being made" and "Play audio that could not be converted".
- [x] 5.2 Add `GET /api/tracks/{id}/audio` returning `{status}`, 404 for an unknown track, no login required; verify with API tests for `PENDING`, `READY`, `FAILED`, an mp3 (`READY` at once), unknown id and an anonymous client, and that `/v3/api-docs` lists the endpoint and the `AudioResponse` schema.
- [x] 5.3 Verify with API tests that the existing byte-for-byte play scenarios still pass for mp3, m4a, ogg, flac and wav, that a WMA track's waveform reaches `READY`, that deleting a WMA track makes `/play` and `/audio` answer 404, and that editing and deleting a track whose audio is `FAILED` succeed.

## 6. Live event and UI

Commit: `feat(ui): show wma audio being prepared and play it when ready`

- [x] 6.1 In `LiveBridge`, publish `audio.prepared` to everyone with the track id on `AudioPrepared`; verify in `LiveBridgeTest` and with a live API test that an anonymous and an authenticated client both receive it, and that no event is raised for an mp3 upload.
- [x] 6.2 Add the audio state call to the track service in `Tracks.ts`, called only for tracks whose format is `WMA`; verify in `Tracks.test.ts`.
- [x] 6.3 In `TrackList.svelte`, show "Preparing audio" or "Audio could not be prepared" as text next to a disabled play control, with theme tokens only and a `role="status"` announcement, refetch the state on `audio.prepared` with no polling, and have `PlayerBar.svelte` say "This audio cannot be played yet" when the browser cannot load the loaded track; verify with component tests that the status is replaced by a play action when the event arrives, that a failed track has no play action but keeps edit and delete, and `cd app && bun run lint && bun run check && bun run format`.
- [x] 6.4 Add a Playwright e2e test in `app/e2e` that mocks the API with `page.route`: upload `tune.wma`, see "Preparing audio", then emit `audio.prepared` and see the play control; verify with `npx playwright test e2e/wma-audio.test.js` in `app` (or `./mvnw verify`).

## 7. Documentation and final checks

- [x] 7.1 Update the `README.adoc` features list (WMA support, the rendition, and that ffmpeg is needed to play WMA) and the ffmpeg note at line 110; verify the text matches the implemented behaviour.
- [x] 7.2 Run `./mvnw clean verify` and verify it passes with the frontend lint and check, backend, vitest and Playwright tests.
- [x] 7.3 Check `GLOSSARY.md`: the Track definition and the "audio" wording stay accurate (a rendition is an implementation detail, not a domain term); update it in this change if that proves wrong.
- [ ] 7.4 Push the branch and open a PR titled `feat(media): support wma uploads through an aac rendition` to `main`, only after 7.2 passes, with `Closes #837` in the body; verify the `workflow-pr-title` check is green.

## Workflow follow-up

- Archive the change on the feature branch before merge (`/openspec-archive-change`), so the specs land in `main` with the code.
