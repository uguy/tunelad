# Design

## Context

- `track` stores audio through the `media` module's public `MediaStore` (`store(key, audio, contentType)`,
  `delete(key)`). The key is the track id. `media` keeps the audio in GridFS (`GridFsMediaStore`) and streams it from
  `MediaController` (`GET /api/tracks/{id}/play`). `media` depends only on `infrastructure`, and `track` depends on
  `media`, so `media` can never listen to `track` events (that would be a cycle).
- `TransactionalEvents` (infrastructure) publishes events within a reactive transaction on `boundedElastic`, and
  the Spring Modulith Event Publication Registry (MongoDB) persists them for `@ApplicationModuleListener`s.
- The OCI image is built with Paketo buildpacks (`spring-boot:build-image`). CI runs `./mvnw clean verify` on
  `ubuntu-latest`. ffmpeg is not installed on the maintainer's machine yet.
- UI: `player.svelte.ts` holds only `source = { trackId, start }`. `Player.svelte` renders a native
  `<audio id="player" controls>` plus the copy-link control, inside the layout's content column. `TrackList.svelte`
  uses `<details>` rows. The theme is a two-line `body` rule in `app.css`.

## Goals / Non-Goals

**Goals:**
- Compute waveforms off the request path, safely retried, and without blocking an event loop thread.
- Keep everything waveform-related inside `media`, with no new module dependency.
- One `<audio>` element for the whole app, driven by a single Svelte 5 state object.
- Keep the existing media e2e tests green with little change (`#player` and its `src` with `#t=`, the
  `play track … button` and `Copy link at current time` accessible names).

**Non-Goals:**
- Streaming peaks progressively or computing them in the browser.
- A generic job system. The event registry and the startup backfill are enough.

## Decisions

### D1. ffmpeg as an external process, fed from GridFS, isolated on `boundedElastic`
`FfmpegPeakExtractor` (media internal) runs
`ffmpeg -hide_banner -loglevel error -i pipe:0 -f s16le -ac 1 -ar 8000 pipe:1` with `ProcessBuilder`. It writes the
GridFS content (`Media.content(0, length-1)`) to stdin and reads mono 16-bit PCM from stdout. Process I/O is blocking,
so the whole extraction is a `Mono.fromCallable` on `Schedulers.boundedElastic()`, with a timeout (configurable, by
default 60 s) after which the process is destroyed. stdin is written from a separate `boundedElastic` task so the two
pipes cannot deadlock.

The binary path comes from `tunelad.media.ffmpeg.path` (default `ffmpeg`), bound to a `@ConfigurationProperties`
record.
*Alternatives*: pure-Java SPI decoders (no maintained AAC decoder, so m4a would have no waveform). Client-side Web
Audio decoding (downloads whole files and defeats byte ranges). Both were rejected with the user.

### D2. Peak computation independent of the track's length
While reading PCM, keep the maximum absolute sample per window of 100 samples (80 windows per second, so a 10-minute
track yields 48 000 floats). At the end, group the windows into 200 buckets (max of each), then divide by the global
maximum, so the loudest peak is 1 (all zeros when silent). This is a pure function (`Peaks.of(short[] windowMaxima)`
or similar), unit-tested without ffmpeg.
*Alternative*: RMS per bucket. It looks smoother but is less faithful to transients, and max is what streaming sites
display.

### D3. Storage: a `waveforms` collection owned by `media`
Document `{ _id: <key>, status: PENDING|READY|FAILED, peaks: [double], computedAt }`, as a `@Document` record
`Waveform` in `media.internal`, with a reactive repository. Peaks are a small array (200 doubles), so they are not
stored in GridFS metadata. That keeps the GridFS store focused and lets the backfill query "files without a
waveform" with a simple `$lookup` or set difference.

### D4. Trigger: `media` publishes its own internal event after storing
`GridFsMediaStore.store` becomes: store in GridFS → then, in a short reactive transaction of its own
(`TransactionalOperator`), save `Waveform(key, PENDING)` and publish `AudioStored(key)` via `TransactionalEvents`.
The transaction is needed because uploads store audio *outside* any transaction (so a long write holds none open), and
a module listener never hears about an event published outside one. The listener only *updates* an existing waveform
(no upsert), so a waveform deleted while it was being computed (failed upload, quick delete) does not come back. An
`@ApplicationModuleListener` in `media.internal` (`WaveformComputer`) handles it: it extracts the peaks and saves
`READY` or `FAILED`. Because it goes through the Event Publication Registry, an event lost to a crash or redeploy is
republished on restart, which is the idiomatic Modulith way. The upload response never waits for ffmpeg.
`AudioStored` is internal to `media` (not a `@NamedInterface`), since no other module needs it.
`delete(key)` also deletes the `Waveform` document.
*Alternatives*: compute inline in `store` (slows uploads, 413/timeout risk), or listen to `NewTrackSaved`
(forbidden: a cycle between `media` and `track`).

### D5. ffmpeg missing vs. undecodable audio
If the process cannot start (`IOException` on `start()`), the waveform stays `PENDING`, a single WARN says ffmpeg
is missing, and the listener completes normally so the event is not retried forever. The startup backfill then
retries on the next start, once ffmpeg is installed. If ffmpeg starts but exits non-zero or produces no samples, the
waveform becomes `FAILED`. It is not retried, since the file will not change.

### D6. Backfill at startup
`WaveformBackfill` listens to `ApplicationReadyEvent`. It finds GridFS files whose filename has no `READY` or
`FAILED` waveform and processes them with `concatMap` (one ffmpeg at a time, so a large backlog cannot saturate the
CPU), subscribed in the background. It never delays startup. It reuses the same compute-and-save code as D4.

### D7. REST endpoint in `MediaController`
`GET /api/tracks/{id}/waveform` → `WaveformResponse(status, peaks)`. It returns 404 when there is no GridFS file for
the key, and `{status: PENDING, peaks: []}` when the file exists but no waveform document does yet. It is public in
`SecurityConfig` like `/play`. The OpenAPI description gains the operation under the "Media" tag, a `Waveform` schema
(status enum, `peaks` with `minItems`/`maxItems` 200 when ready) and the 404 response. The
`Cache-Control: max-age=3600` header is set only on `READY` responses.

### D8. UI player state: one store, one `<audio>`
`player.svelte.ts` grows into a `PlayerState` class with `$state` fields: `queue: Track[]`, `index`,
`source` (kept: `{ trackId, start }`), `playing`, `currentTime`, `duration`, `volume` and `muted` (persisted in
`localStorage`). It also exposes the methods `playFrom(queue, index, start?)`, `play(trackId, start)` (deep links,
empty queue), `toggle()`, `seek(seconds)`, `next()`, `previous()`, `setVolume()` and `toggleMute()`. The current
`Track` is either `queue[index]` or fetched by id for deep links (`TrackService.findOne`), so the bar can show the
title and owner.

`PlayerBar.svelte` (in `$lib`, rendered by `+layout.svelte` outside `<main>` and only once `player.source` is set)
owns the single `<audio id="player" preload="none">`, without `controls`. Its `src` is computed exactly as today
(`/api/tracks/{id}/play#t=…`), so the deep-link tests still hold. It binds `currentTime`, `duration` and `paused`
two-way to the state and calls `next()` on `ended`. The copy-link logic moves over unchanged. The layout adds bottom
padding while the bar is shown, so it never covers the last row.

Because the bar lives in the root layout, SvelteKit client-side navigation keeps it mounted, so playback continues
across pages.

### D9. Waveform rendering
`Waveform.svelte` takes `peaks`, `progress` (0–1) and `onseek(fraction)`. It renders an inline SVG of 200 `<rect>`
bars (mirrored bars with a lighter reflection below, a common streaming-site style). Played bars use the accent
colour through a `clipPath` sized to `progress`, so only one attribute changes per `timeupdate`. It is a
`role="slider"` with `aria-valuenow` and arrow-key seeking for accessibility. The click fraction comes from
`offsetX / clientWidth`. `TrackList` fetches waveforms lazily per visible row (`IntersectionObserver`), caches them in a
`Map`, and polls a `PENDING` waveform every 3 s for up to 30 s, so a fresh upload fills in.

### D10. Theme with Tailwind v4 `@theme` tokens
In `app.css`: `@theme { --color-accent: #ea580c; --color-accent-hover: #c2410c; --color-ink: #1f1f24;
--color-surface: #ffffff; --color-canvas: #f2f2f2; --font-sans: … }`, so components use `bg-accent`, `text-ink`, and
so on. The accent is Tailwind's orange-600, not any brand's exact orange: white on it meets 3:1 for icons and large
text, and text-sized accent uses `--color-accent-hover` (≥4.5:1 on white). Header: dark ink bar, logo, nav and
account menu. Rows: white cards on the light grey canvas. The artwork tile is a gradient whose two hues are derived
from a hash of the track id, so it is stable and needs no stored artwork.

### D11. Test selectors
New rows expose `data-testid="track-title"`. Tests using `summary span.px-2` switch to it. Row buttons keep their
accessible names (`play track {title} button` while not playing, `pause track {title} button` while playing;
`edit track …`, `delete track …`). Playwright covers the new player behaviours by stubbing `/api/tracks*` routes, as
`player.test.js` does today, with a short generated audio fixture so `ended` and seeking are fast.

## Risks / Trade-offs

- [ffmpeg absent locally or on a deployment] → D5 degrades gracefully to flat placeholders. The README lists ffmpeg
  as a prerequisite. Backend tests that need it fail with a clear message rather than silently skipping, so CI keeps
  checking them.
- [Many uploads at once spawn many ffmpeg processes] → module listeners run on the async executor with bounded
  concurrency, and backfill is sequential. ffmpeg on 8 kHz mono output is fast (well under a second per minute of
  audio).
- [Large files piped through the JVM] → content streams chunk by chunk from GridFS to stdin and is never buffered
  whole. Only window maxima are kept (D2).
- [Timing-dependent tests (waveform "within a few seconds")] → use Awaitility with a 10 s bound, as the search
  projection tests do.
- [Hidden `<audio>` and autoplay policies] → playback always starts from a user click, except deep links, which
  already rely on that behaviour today.
- [Big UI diff in one PR] → split into reviewable commits per task group (theme, player state and bar, waveform rows,
  pages).

## Migration Plan

Additive. On first start after deploy, the backfill computes waveforms for existing tracks in the background. Until
then the UI shows placeholders. Rollback: deploy the previous version. The `waveforms` collection is ignored by it
and can be dropped.

## Open Questions

- Packaging ffmpeg in the OCI image (custom run image vs. a community apt buildpack) is deferred to a follow-up, as
  stated in the proposal. It changes neither the specs nor this change's tasks.
