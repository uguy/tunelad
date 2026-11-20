# Tasks

## 1. Setup

- [x] 1.1 Create branch `feat/ui-redesign-waveform-player` from an up-to-date `main` and verify with `git branch --show-current`
- [x] 1.2 Make sure ffmpeg is installed locally (add it to `mise.toml` if a mise tool exists for it, otherwise install it with the system package manager) and verify `ffmpeg -version` succeeds; confirm the `ubuntu-latest` CI image provides it, or add an install step to `workflow-build.yml`

## 2. Waveform computation in the media module (commit: `feat(media): compute waveform peaks after audio is stored`)

- [x] 2.1 Add the pure peak reduction (window maxima → 200 buckets → normalised to the loudest peak, all zeros when silent) and verify with unit tests for normalisation, silence, and inputs shorter than 200 windows
- [x] 2.2 Add the `tunelad.media.ffmpeg` configuration properties (path, timeout) and `FfmpegPeakExtractor`, which pipes GridFS content to ffmpeg on `boundedElastic` and reads s16le PCM; verify with a test on the existing wav/mp3/m4a/ogg/flac fixtures that each gives 200 peaks, and that random bytes give a decode failure
- [x] 2.3 Add the `Waveform` document and reactive repository (`waveforms` collection), make `GridFsMediaStore.store` save a `PENDING` waveform and publish the internal `AudioStored` event via `TransactionalEvents`, and make `delete` remove the waveform; verify with Testcontainers tests
- [x] 2.4 Add the `@ApplicationModuleListener` that computes and saves `READY`/`FAILED`, and leaves `PENDING` with a single WARN when ffmpeg cannot start; verify with Testcontainers + Awaitility tests (silent-then-loud wav peaks, random bytes → `FAILED`), and that `ModularApplicationTests` still passes
- [x] 2.5 Add the `ApplicationReadyEvent` backfill that processes, one at a time, stored audio with no `READY`/`FAILED` waveform; verify with a test that stores GridFS audio directly with no waveform and sees it become `READY`
- [x] 2.6 Document ffmpeg as a prerequisite in README.adoc (Prerequisite section) and verify the documented `ffmpeg -version` check

## 3. Waveform REST endpoint (commit: `feat(api): expose track waveforms`)

- [x] 3.1 Add `GET /api/tracks/{id}/waveform` to `MediaController` (`READY`/`PENDING`/`FAILED`, 404 for unknown id, cache header on `READY` only) with OpenAPI annotations and a `Waveform` schema; verify with API tests for each status, 404, and anonymous access (allowed in `SecurityConfig`)
- [x] 3.2 Add an API test that a track's waveform is 404 after the track is deleted through `DELETE /api/tracks/{id}`, and an end-to-end upload test that polls for `READY` within 10 s for each supported format
- [x] 3.3 Verify the generated OpenAPI document (`/v3/api-docs`) contains the waveform operation under the "Media" tag

## 4. Theme and layout shell (commit: `feat(ui): add the new theme and restyle the header and layout`)

- [ ] 4.1 Define the `@theme` tokens (accent, accent-hover, ink, surface, canvas, font) in `app/src/app.css` and verify `npm run check` and `npm run lint` pass
- [ ] 4.2 Restyle `Header.svelte` (dark bar, logo, nav, account menu, mobile menu) and `Footer.svelte` with the tokens, keeping their accessible names; verify `account.test.js` and `index.test.js` still pass

## 5. Player state and player bar (commit: `feat(ui): replace the native audio controls with a persistent player bar`)

- [ ] 5.1 Extend `player.svelte.ts` into the full player state (queue, index, source, playing, currentTime, duration, volume/mute persisted in localStorage; playFrom/play/toggle/seek/next/previous) and verify with vitest unit tests for next/previous rules (3-second restart, disabled next on last/empty queue) and volume persistence
- [ ] 5.2 Add `PlayerBar.svelte` in `$lib`, which owns the single `<audio id="player">` (same `src`/`#t=` logic, no native controls): play/pause, previous/next, progress slider with elapsed/duration, volume and mute, current title and owner (fetched for deep links), and the copy-link control moved from `Player.svelte`; render it from `+layout.svelte` only once a track is loaded, with bottom padding; remove `Player.svelte`
- [ ] 5.3 Update and extend `app/e2e/player.test.js`: the existing deep-link and copy-link tests still pass, plus new tests for the bar appearing, pause/resume, next/previous, auto-advance on `ended`, seeking with the progress control, mute and remembered volume, and playback continuing after navigating to `/about`

## 6. Track rows with waveforms (commit: `feat(ui): redesign track rows with a clickable waveform`)

- [ ] 6.1 Add `Waveform.svelte` (SVG bars, played-part highlight via clip path, slider role with arrow keys, flat placeholder when not ready, `onseek(fraction)`) and verify with a vitest browser component test that clicking at a fraction reports it and that progress highlights the right share of bars
- [ ] 6.2 Add `findWaveform(id)` to `Tracks.ts` and verify with a unit test in `Tracks.test.ts`
- [ ] 6.3 Rewrite `TrackList.svelte` as rows (generated artwork tile, round play/pause button reflecting player state, owner and title with `data-testid="track-title"`, tags, waveform loaded lazily and polled while `PENDING`, expandable description, edit/delete) wired to `player.playFrom` with the shown list as queue; update `track-upload.test.js` and `track-ownership.test.js` selectors and add e2e tests for clicking a loaded track's waveform to seek, clicking another track's waveform to start it there, and the pending placeholder

## 7. Restyle the remaining pages (commit: `feat(ui): restyle home, about, tracks, upload and placeholder pages`)

- [ ] 7.1 Restyle the tracks page (search, sub-navigation) and the upload page (form, drop zone, progress bar) with the tokens, keeping their accessible names; verify `track-upload.test.js` passes
- [ ] 7.2 Restyle home, about, favourites, playlists and albums; verify `index.test.js` passes and check every page by running the app (`docker compose up -d`, `./mvnw spring-boot:run`, `cd app && npm run dev`) on desktop and mobile widths

## 8. Integration and delivery

- [ ] 8.1 Run `cd app && npm run format`, then `./mvnw clean verify`, and verify it passes (Java tests, vitest, Playwright, lint, svelte-check, `ModularApplicationTests`)
- [ ] 8.2 Update README.adoc Features (waveforms, persistent player bar with previous/next and volume) and verify the list matches the shipped behaviour
- [ ] 8.3 Push the branch and open a PR to `main` titled `feat(ui): redesign the ui with a waveform player`; verify the `workflow-pr-title` and `build` checks pass

## Workflow follow-up

- Archive the change with `/openspec-archive-change` on the feature branch before merging, so the media spec lands on main together with the code.
- Follow-up change: ship ffmpeg in the OCI image built by `spring-boot:build-image`.
