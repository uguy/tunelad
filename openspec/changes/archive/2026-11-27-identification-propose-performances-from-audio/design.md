## Context

`track` stores Performances (`track_performances`: offset, key, notes, abc) and publishes `NewTrackSaved(TrackDTO)` in `track::event`. Nothing analyses audio.
The research (`docs/research/identification-sources.md`) found that the bare FolkFriend CLI fails on a real duo recording, while Cadence's pipeline (note
decoder, tuned FolkFriend query, Viterbi segmenter) identifies the first tune of the same recording in every window from 5 s to 105 s.

## Goals / Non-Goals

**Goals:** background, non-blocking proposals of each Performance's start offset; states `pending`/`identified`/`corrected`; corrections always win; the engine behind a port so it can be replaced or fail without harm.
**Non-Goals:** proposing key or Transcription (later slice), proposing the Set, hosting models, a Java port of the engine, irishtune.info, live progress (#824).

## Decisions

- **Sidecar running Cadence's engine.** `sidecar/identification/` is a Node container (Node 22+, no browser): it loads the vendored FolkFriend WASM (SIMD) and transcriptor WASM, the thesession.org index, and runs Cadence's `TranscriptorEngine` and `IncrementalViterbiSegmenter` over 10 s windows with a 5 s hop, as the spike harness did (8 s for 152 s of audio). The engine code is copied from Cadence at a pinned commit with its licence files; `NOTICE` says where it comes from.
- **Wrapper API.** `POST /identify` takes the audio (any format ffmpeg reads; the wrapper decodes to 48 kHz mono PCM) and a list of tune ids, and returns `[{ tuneId, start, end, confidence }]` for detections, each start being the segmenter's first window start. `GET /health` for compose and tests. The index is downloaded once on start and cached on a volume.
- **Matching is constrained to the Set.** The Java side passes the Set's tune ids; the sidecar drops detections of other tunes. The Java side also checks it, so a misbehaving sidecar cannot change a Performance of another Tune.
- **Module `identification`**, `allowedDependencies = { "infrastructure", "track::event", "track::command", "media", "tune" }`. It listens to `NewTrackSaved` (via `@ApplicationModuleListener`, asynchronous, after commit) and applies proposals through a `track::command`; it never touches `track.internal`. `track` must not depend on `identification`, so the re-run endpoint lives in `identification.web`.
- **Port and adapter.** `IdentificationSource` returns `Flux<DetectedTune>`; `CadenceSource` implements it with `WebClient`, streaming the stored audio from the `media` public API as a `Flux<DataBuffer>` (no blocking read), with a connect and a response timeout. No Java segmenter: segmentation lives in the sidecar.
- **State in `track`.** New column `identification TEXT NOT NULL` on `track_performances` (`pending|identified|nothing_found|corrected`), default `corrected` for existing rows and `pending` for new ones; the API writes the values in lowercase (`@JsonValue`). A track command `ProposePerformances` carries the proposals and the positions that were searched: it updates only rows not `corrected`, and turns the searched rows nothing was proposed for from `pending` into `nothing_found`, so the rule is enforced in one place. A run that could not be done (service down) writes nothing, so those rows stay `pending`. `UpdateTrackCommand` sets `corrected` for rows whose values changed.
- **Concurrency.** The command locks the track's row (`SELECT … FOR UPDATE`) and writes the Performances only, not the track's own row: an edit made while a run finishes neither fails on the track's version nor loses an update.
- **Failures.** Errors are logged per run; states stay unchanged; the re-run endpoint is the retry. Applying proposals is idempotent (keyed by Performance position).
- **REST / OpenAPI.** `PerformanceDTO` gains `identification` (enum schema `IdentificationState`); `POST /api/tracks/{id}/identification` documented as 202/403/404. Additive, not breaking.
- **UI.** A small state badge (theme tokens, text not colour alone, accessible name) per Performance on the tune view and a "Run identification again" button for owners; refetch through `refreshOnLive`.
- **Licence.** The sidecar contains AGPL-3.0 (Cadence) and GPL-3.0-only (vendored FolkFriend) code. It runs as its own container, talks to the app over HTTP only, and its source stays public in this repository with its own `LICENSE`; the rest of the repository keeps its licence. AGPL §13 is met because users of the service can get its source from the repository.

## Risks / Trade-offs

- Accuracy: one recording tested. Offsets are half-hop accurate (about 2.5 s), and one false positive appeared at the end of the file; Set-constrained matching and editability limit the harm.
- Tunes missing from the thesession.org index (the second reel of the test file seems to be one) are never identified and stay `pending`.
- Cadence's engine is not a stable library: pinned commit, a golden test on a small fixture, and the port keeps it replaceable.
- Extra container (about 100 MB of WASM, Node, ffmpeg and a 33 MB index), added to compose and CI.
- Async work lost on restart: Spring Modulith's event publication registry replays incomplete listeners.

## Migration Plan

Liquibase changeset adds the column (existing rows `corrected`, so nothing changes for old data); rollback drops it. The sidecar is additive; with it down, uploads work and Identification fails safely.

## Open Questions

- Key and Transcription proposals (next slice): derive from the transcriptor's contour output?
