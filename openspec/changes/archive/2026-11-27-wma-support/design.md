# Design

## Context

- `media` stores audio as chunked rows (`media_files` and `media_chunks`) under a caller-chosen key, which is the track id.
  `ChunkedMediaStore.store` saves a `PENDING` waveform and publishes `AudioStored` in the same transaction. `WaveformComputer`
  listens, runs ffmpeg through `FfmpegPeakExtractor` on the bounded elastic scheduler, saves `READY` or `FAILED`, and
  publishes `WaveformComputed`; `WaveformBackfill` finishes pending ones at startup. `live` bridges `WaveformComputed` to
  `waveform.computed`, and `TrackList.svelte` refetches waveforms on that event.
- `MediaController.play` reads `media_files.content_type` and streams the stored bytes, so today the played bytes are the
  uploaded bytes.
- `track` stores audio with `TrackFormat.mimeType()` as the content type. `TrackFormat.forFile` picks the format from the
  extension. `identification` reads the stored audio through `MediaStore.read(trackId)`, and ffmpeg decodes WMA as it is.
- Browsers have no WMA decoder, so a rendition is needed. See proposal.md - Why.

The waveform pipeline is exactly the shape needed: derived data, computed after commit, with a state, a live event and a
backfill. The rendition copies it.

## Goals / Non-Goals

**Goals:**
- A WMA upload plays in the browser, with `Range` support, through a rendition made in the background.
- One mechanism that decides, from the stored content type, whether a rendition is needed, so a later non-browser format
  only adds an enum value.
- `track` stays unaware of renditions: it only gains a format.

**Non-Goals:**
- Choosing quality settings per user, or several renditions per audio.
- Moving `identification` or the waveform onto the rendition. They keep the original, which is the most faithful source.

## Decisions

### 1. The rendition is a second `media_files` row, with its state in a new table

The rendition is stored under the key `<key>:play`, as a normal `media_files` row with `content_type = audio/mp4`. A new
table `media_renditions (file_key PK REFERENCES media_files ON DELETE CASCADE, status CHECK IN ('PENDING','READY','FAILED'),
computed_at)` holds its state, keyed by the original's key. A row exists only for audio that needs a rendition. No row
means `READY` (browser formats, and audio stored before this change).

- `ChunkedMediaStore.keys()` (used by the waveform backfill) excludes keys ending in `:play`, so a rendition never gets a
  waveform of its own.
- `ChunkedMediaStore.delete(key)` also deletes `<key>:play`. The `ON DELETE CASCADE` removes the state row. The delete
  stays one place, so `DefaultTrackService` is untouched.
- Reusing the chunk store gives `Range` support for the rendition for free, since `play` already reads a `Media`.

Alternatives: a `variant` column in the primary key of `media_files` and `media_chunks` (a larger migration touching every
query, for no new behaviour); storing the rendition as a column on the original row (a second large blob in one row,
which breaks the chunked read); a separate object store (a new dependency for a pet project).

### 2. What needs a rendition is told by the stored content type

`ChunkedMediaStore.store` already receives the content type. A small `Playable` rule in `media::internal` says which
content types a browser plays (`audio/mpeg`, `audio/mp4`, `audio/ogg`, `audio/flac`, `audio/wav`); the others need a
rendition. `store` saves a `PENDING` rendition row next to the pending waveform, in the same transaction, and the existing
`AudioStored` event also drives the conversion. This keeps `media` free of `TrackFormat`, which `media` cannot depend on
(`media` allows only `infrastructure`).

`TrackFormat` gets `WMA("audio/x-ms-wma", "wma")`, whose `mimeType()` is the stored content type. The "stored as" and
"played as" split in the proposal lives in `media`, not in the enum: the enum says what the file is, and `media` knows
what browsers play and serves `audio/mp4` for the rendition. This corrects the proposal's wording, which said
`TrackFormat` would separate the two.

### 3. Conversion: ffmpeg to a temporary file, then stored

`RenditionMaker` (new, `media::internal`) runs `ffmpeg -i pipe:0 -vn -c:a aac -b:a 128k -movflags +faststart <tmp>.m4a`.
The original streams to stdin as `FfmpegPeakExtractor` already does. The output goes to a temporary file because the mp4
muxer must seek back to write the `moov` atom, which a pipe does not allow; fragmented mp4 to a pipe would stream, but
browsers seek badly in it. The file is then read in chunks into `ChunkedMediaStore.store(<key>:play, ...)`, and deleted in
a `finally`. All process and file I/O run on `Schedulers.boundedElastic()`, never on an event loop thread, with the same
`FfmpegProperties` timeout. The default timeout of 60 s is raised for conversion through a new `tunelad.media.ffmpeg.transcode-timeout`
property (default `5m`), since encoding a 10-minute track takes longer than decoding it for peaks.

Alternatives: output to a pipe as fragmented mp4 (poor seeking); mp3 through libmp3lame (not every ffmpeg build has it,
while the native `aac` encoder is always there); ogg/opus (Safari support is recent).

### 4. State, event and play behaviour mirror the waveform

- `RenditionComputer` (like `WaveformComputer`) listens to `AudioStored` with `@ApplicationModuleListener`, blocks on a task
  executor thread, skips audio with no rendition row, and saves `READY` or `FAILED`. `UndecodableAudioException` gives
  `FAILED`; `FfmpegUnavailableException` leaves it `PENDING` with one warning.
- A new public `media::event.AudioPrepared(String key)` is published, in a transaction of its own as `WaveformComputer.save`
  does, when the state changes from `PENDING` to `READY` or `FAILED`, and only when a row was updated.
- `MediaController.play` looks up the rendition state first: no row or `READY` serves the right key (the original, or
  `<key>:play`); `PENDING` answers 503 with `Retry-After: 5`; `FAILED` answers 422. A new `GET /api/tracks/{id}/audio`
  returns `{status}`, answering 404 when the original is missing.
- `live` gets one listener for `AudioPrepared`, published as the library event `audio.prepared` with the track id. `live`
  already allows `media::event`.
- `RenditionBackfill` (like `WaveformBackfill`) converts, one at a time, rendition rows still `PENDING` at startup.

### 5. UI

`Tracks.ts` gains `wma` in `SUPPORTED_EXTENSIONS` and `audio/x-ms-wma` in `ACCEPTED_FILES`. Only tracks whose `format` is
`WMA` fetch `GET /api/tracks/{id}/audio`, so existing rows cost no new request. `TrackList.svelte` and the player bar show
"Preparing audio" or "Audio could not be prepared" as text next to a disabled play control, and refetch the state on
`audio.prepared`, as they do for waveforms (`live.on`, with no polling). Colours come from theme tokens only.

### 6. OpenAPI and docs

`MediaController` documents the new endpoint and the 503 and 422 responses on `/play`, and the `produces` list is
unchanged because the rendition is `audio/mp4`. `TrackController`'s 415 text lists wma. `README.adoc` lists WMA among the
formats and notes that ffmpeg is needed to play it.

## Risks / Trade-offs

- [A temporary file is written to disk during conversion] → Use `Files.createTempFile` and delete it in a `finally`; the
  100 MB upload limit bounds its size. Document that the temp directory must be writable.
- [Conversion is slow for long files, so a track is silent for a while] → The state, the live event and the UI text make
  the wait visible; the upload response is never delayed.
- [A burst of uploads starts many ffmpeg processes] → Listeners run on the task executor, which bounds them; the backfill is
  sequential. Revisit if it matters.
- [A rendition doubles the storage of a WMA track] → Accepted; AAC at 128 kb/s is smaller than most WMA sources.
- [AAC transcoding is lossy] → The original is kept unchanged, so a better rendition can be made later.
- [A `:play` suffix could collide with a track id] → Track ids are UUIDs, which never contain a colon.
- [Tests need an ffmpeg that encodes WMA and AAC] → The CI workflows already install ffmpeg from apt, which includes the
  `wmav2` and `aac` encoders; a test assumes ffmpeg is present and is skipped with a clear message when it is not.

## Migration Plan

A Liquibase changeset in `media.sql` creates `media_renditions`. It needs no backfill, because no stored audio is WMA and
audio with no row counts as `READY`. Rollback drops the table; WMA tracks stored meanwhile would then play their original
bytes (silence in browsers) until the table is restored, so roll back only before any WMA is uploaded.

## Open Questions

- Whether to expose the original WMA for download later. It does not change this design, since the original is kept.
