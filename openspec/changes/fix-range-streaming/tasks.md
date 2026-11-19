# Tasks

## 1. Branch and plan

- [x] 1.1 Create `feat/range-streaming` from an up-to-date `main` (`git switch main && git pull && git switch -c feat/range-streaming`), carrying the untracked `openspec/changes/fix-range-streaming/`, and verify `git branch --show-current` prints `feat/range-streaming`
- [x] 1.2 Commit the change artifacts as `docs(openspec): plan range streaming and start-at-position playback` and verify `openspec validate fix-range-streaming --strict` passes

## 2. Media handle with chunk-level reads (whole-file responses)

- [x] 2.1 Add the public `TrackMedia` interface (`length()`, `content(first, last)`) in `org.tunelad.track` and verify `ModularApplicationTests` still passes
- [x] 2.2 Change `Tracks.readMedia` to `Mono<TrackMedia> readMedia(String id)` and implement it in `DbTracks`: `GridFSFile` lookup by filename, an `fs.chunks` query on `n` in `[first/chunkSize, last/chunkSize]` sorted by `n`, first and last chunk trimmed, bucket name from configuration with default `fs` (design Decisions 2 and 3). Verify the exact Boot 4.1 property key while doing so
- [x] 2.3 Change `TrackService.execute(PlayTrackCommand)` to return `Mono<TrackMedia>` (still 404 for an unknown track), and make `TrackController.play` take the `Range` header as optional and always answer 200 with `Content-Type: audio/mpeg`, `Content-Length` and `Accept-Ranges: bytes`, streaming `content(0, length - 1)`
- [x] 2.4 Add the `play(id, range)` overload to `AbstractApiTest` (null omits the header) and the test `play_without_a_range_header` (200, headers, byte-exact body). Verify `./mvnw -Dtest='TrackApiTest,TrackSearchApiTest' test` passes, including the unchanged `play_an_uploaded_track`, `play_an_unknown_track` and `clear_a_populated_library`
- [x] 2.5 Commit as `feat(track): read track audio by gridfs chunk and allow play without range`

## 3. Byte ranges: 206, 416 and ignored headers

- [x] 3.1 In `TrackController.play`, parse the header with `HttpRange.parseRanges`: one range gives 206 with `Content-Range`, `Content-Length` and `content(first, last)` via `getRangeStart`/`getRangeEnd(length)`; a start at or past the end gives 416 with `Content-Range: bytes */<length>`; malformed or multi-range headers give the 200 whole-file response (design Decision 1)
- [x] 3.2 Document the play operation in OpenAPI: optional `Range` header parameter and 200/206/404/416 responses with `Content-Range`/`Accept-Ranges` headers (design Decision 5). Verify `GET /v3/api-docs` (or the configured springdoc path) shows them by adding an assertion on the api-docs JSON to `TrackApiTest`, or by checking manually with `curl` against `./mvnw spring-boot:run`
- [x] 3.3 Add one Testcontainers test per new spec scenario, named after it: `range_in_the_middle_of_the_file` (`bytes=1000000-1499999` on `Knees_Up.mp3`, byte-exact slice), `open_ended_range`, `range_from_the_start_as_sent_by_a_browser`, `suffix_range`, `range_ending_past_the_end_of_the_file`, `range_starting_past_the_end_of_the_file`, `unsatisfiable_range_on_an_unknown_track`, `several_ranges`, `malformed_range`. Verify `./mvnw -Dtest=TrackApiTest test` passes
- [x] 3.4 Check by hand in a browser (`docker compose up -d`, `./mvnw spring-boot:run`, upload a track at http://localhost:8080): dragging the audio scrubber jumps to that position, and the devtools Network tab shows 206 responses with `Content-Range`
- [x] 3.5 Commit as `feat(track): serve byte ranges of track audio`

## 4. UI: deep link and copy link at current time

- [x] 4.1 Change `#lib/store/media-source` to hold `{ trackId, start } | null` (initially `null`), and update the play button handler in `tracks/+page.svelte` to set `{ trackId, start: 0 }`
- [x] 4.2 In `Player.svelte`, derive `src` from the store (`#t=<start>` when `start > 0`), bind `currentTime`, and add the "Copy link at current time" button, shown only when a track is loaded, which writes the absolute `/tracks?play=<id>&t=<floor(currentTime)>` URL to the clipboard, with "Link copied" feedback and a visible-text fallback when the clipboard rejects (design Decision 6)
- [x] 4.3 In `tracks/+page.svelte`, on mount read `play` and `t` from `page.url.searchParams` (`$app/state`) and set the store (NaN or negative `t` becomes 0). Verify `cd app && npm run build` still prerenders without errors
- [x] 4.4 Add `app/e2e/player.test.js` with `page.route` mocks for the search and play endpoints, one test per UI scenario: `open a link with a start time`, `open a link without a start time`, `open a link with an invalid start time`, `copy a link while a track is playing`, `no track loaded`. Verify `cd app && npx playwright test e2e/player.test.js` passes
- [x] 4.5 Run `cd app && npm run format && npm run lint`, check the flow by hand with `npm run dev` against the running backend (open a copied link in a new tab and check it starts at that time), and commit as `feat(ui): start playback at a position from a shareable link`

## 5. Docs, verification and PR

- [x] 5.1 Update the README.adoc feature list: keep "streaming bytes with range support" (now true) and add "Share a link that starts a track at a given time". Verify it renders with an AsciiDoc preview, then commit as `docs(track): document start-at-position links`
- [x] 5.2 Run `./mvnw clean verify` and verify it passes (frontend build, all tests, `ModularApplicationTests`)
- [ ] 5.3 Push `feat/range-streaming` and open a PR to `main` titled `feat(track): stream byte ranges and start playback at a position`. Verify the `workflow-pr-title` and build checks pass

## Workflow follow-up

- Run `/openspec-archive-change` on the feature branch before merge, so `openspec/specs/track/spec.md` lands on `main` with the code.
- At archive time, update the `track` spec's Purpose (currently "through the REST API") to also mention UI playback links, since the spec now has UI requirements.
