# Tasks

## 1. Branch

Commit: `docs(openspec): propose media-module change`

- [x] 1.1 Create branch `refactor/media-module` from an up-to-date `main` (`git switch main && git pull && git switch -c refactor/media-module`) and commit `openspec/changes/media-module/`. Verify that `git branch --show-current` prints `refactor/media-module` and that `git status` is clean.

## 2. Media module with audio storage

Commit: `refactor(media): add a media module that stores audio in gridfs`

- [x] 2.1 Create the `media` module (design D1): `org.tunelad.media.package-info` with `allowedDependencies = { "infrastructure" }`, and the public `MediaStore` interface in the base package (`Mono<Void> store(String key, Publisher<DataBuffer> audio)`). Verify that `ModularApplicationTests` passes and lists `media` as a module.
- [x] 2.2 In `org.tunelad.media.internal`, add `Media` (the contract of `TrackMedia`) and `GridFsMediaStore`, which implements `MediaStore` and adds `Mono<Media> find(String key)`. Copy the GridFS code from `DbTracks` (`saveMedia`, `readMedia`, `GridFsTrackMedia`, same bucket property and `filename = key`, design D2) without changing its behaviour. Leave `track` untouched in this group. Verify with an `@ApplicationModuleTest` for `media` (Testcontainers Mongo, run with all tests forced): storing the Knees Up fixture under a key and reading it back with `find` gives the same bytes; a slice crossing a chunk boundary matches the fixture's bytes; an unknown key gives an empty `Mono`.

## 3. Upload stores audio through media

Commit: `refactor(track): store uploaded audio through the media module`

- [x] 3.1 Add `media` to `track/package-info.java` `allowedDependencies`. Change `DefaultTrackService.execute(AddTrackCommand)` to `tracks.save → mediaStore.store(saved.getId(), command.data()) → events.publish(NewTrackSaved)` (design D3), and remove `Tracks.saveMedia` and its `DbTracks` implementation. Verify that `TrackApiTest` (upload and play tests, play is still served by `track` from the same GridFS files), `TrackEventsTest` and `ModularApplicationTests` pass with all tests forced.

## 4. Serve play from media

Commit: `refactor(media): serve track audio from the media module`

- [x] 4.1 Add `org.tunelad.media.web.MediaController` with `GET /api/tracks/{id}/play`. Move the play method, `singleRange`, `wholeFile`, `partial` and the OpenAPI annotations from `TrackController` unchanged. Read with `GridFsMediaStore.find`, and error with `NotFoundException` when it is empty. Tag the controller `Media` ("Stored audio and playback") (design D4). In the same step, remove `play` and its helpers from `TrackController`, so that two handlers never map the same path. Verify that the app context starts (`TuneladApplicationTests`).
- [x] 4.2 Delete `PlayTrackCommand`, `TrackService.execute(PlayTrackCommand)` and its implementation, `TrackMedia`, `Tracks.readMedia`, and `GridFsTrackMedia` and the GridFS fields from `DbTracks`. Verify that `grep -rn "PlayTrackCommand\|TrackMedia\|readMedia\|GridFs" src/main/java/org/tunelad/track` finds nothing, and that `ModularApplicationTests` passes (it fails if `media` references `track`).
- [x] 4.3 Move the play and range tests from `TrackApiTest` to a new `org.tunelad.media.MediaApiTest extends AbstractApiTest`: every test under the "Play a track", "Serve a byte range", "Reject an unsatisfiable byte range" and "Ignore unsupported Range headers" sections, plus `play_is_documented_with_range_responses` and the helpers they use. Keep their names and bodies. Add an assertion that the play operation is tagged `Media` in `/v3/api-docs` (design D5). Verify that `MediaApiTest` and the reduced `TrackApiTest` pass with all tests forced, and that the number of test methods across the two classes equals the old `TrackApiTest` count.
- [x] 4.4 Run the Playwright e2e tests (`./mvnw verify -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false`). Verify that the player scenarios ("open a link with a start time", "copy a link while a track is playing", and the others) pass with no change in `app/`.

## 5. Verify, document, open the PR

- [ ] 5.1 Run `./mvnw clean verify` with Docker running. If Spring Modulith's test selection skips any module's tests, run again with all tests forced (`-Dspring.modulith.test.file-modification-detector=reference-commit -Dspring.modulith.test.reference-commit=$(git rev-parse HEAD) -Dspring.modulith.test.on-no-changes=execute-all`). Verify that it ends in `BUILD SUCCESS` with no test skipped other than the `@Disabled` `ImporterTest`.
- [ ] 5.2 Update the MongoDB section of `README.adoc`: audio files are stored in GridFS by the `media` module, which also serves playback, and `track` hands the uploaded audio to it. No features-list change (nothing user-visible). Verify that the README no longer suggests that `track` owns the audio. Commit as `docs(media): describe the media module`.
- [ ] 5.3 Push the branch and open a PR to `main` titled `refactor(media): move audio storage and play into a media module`, with a body that summarizes the module split, the unchanged REST contract and data, and the OpenAPI tag change. Verify that the `workflow-pr-title` and build checks pass on the PR.

## Workflow follow-up

- Archive the change on the feature branch with `/openspec-archive-change` before merging, so the new `media` spec and the reduced `track` spec land on `main` with the code.
- When archiving, update the `track` spec's Purpose in `openspec/specs/track/spec.md` to drop playback and the player behaviours, which now belong to `media`. A delta cannot change a Purpose.
- Squash-merge the PR after review.
