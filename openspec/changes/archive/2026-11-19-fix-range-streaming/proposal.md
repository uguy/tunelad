# Proposal

## Why

The README says playback "streams bytes with range support", but `GET /api/tracks/{id}/play` only reads the `Range` header to reject requests that lack it (400). It ignores the header's value and always sends the whole file as a chunked 200, with no `Content-Length`, `Accept-Ranges` or `Content-Range`. Because of this, the browser's audio element cannot seek: dragging the scrubber does nothing or restarts the track, and the duration is often unknown. With range streaming fixed, starting a track at a given position also becomes possible, and the UI can turn that into a shareable link ("listen from 1:30").

## What Changes

- `GET /api/tracks/{id}/play` serves HTTP byte ranges (RFC 9110):
  - the `Range` header becomes optional. A request without it gets the whole file with status 200, `Content-Length` and `Accept-Ranges: bytes` (previously 400);
  - a single satisfiable range (`bytes=S-E`, `bytes=S-` or suffix `bytes=-K`) gets status 206 with exactly those bytes, `Content-Range` and `Content-Length`;
  - a range that starts past the end of the file gets status 416 with `Content-Range: bytes */<length>`;
  - multi-range and malformed `Range` headers are ignored, and the whole file is sent with status 200.
- The backend reads only the GridFS chunks that cover the requested range, rather than streaming the file from byte 0.
- The UI supports starting playback at a position:
  - a deep link `/tracks?play=<id>&t=<seconds>` opens the tracks page with the player loaded on that track at that time;
  - a "copy link at current time" control next to the player copies such a link for the current track and position.
- Seeking with the native audio controls works as a result of the backend change, with no UI change.
- Observable REST change, not breaking: a request with `Range: bytes=0-` (what browsers send) now gets 206 instead of 200, with the same body. A request without `Range` now succeeds instead of failing with 400.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `track`: "Play a track" no longer requires a `Range` header and states the full-file response headers. New requirements cover serving byte ranges (206/416), starting playback at a position from a deep link, and copying a link to the current playback position.

## Out of scope

- Multipart `multipart/byteranges` responses to multi-range requests (they are answered with the full file instead).
- Conditional range requests (`If-Range`), `ETag`/`Last-Modified` caching headers.
- Formats other than mp3, and changing the `audio/mpeg` content type.
- Exact positioning in VBR mp3 files: the browser estimates the byte offset for a start time, so VBR files may start slightly off.
- Forcing autoplay on deep links when the browser's autoplay policy blocks it (the player is loaded at the position, and the user may have to press play).
- Repairing the stale Playwright index test and running the e2e suite in the Maven build.
- A start-time input per track (seeking and deep links cover the need).

## Impact

- **Modulith modules**: `track` only (web, public API package, `internal`, `internal.db`). No new cross-module dependencies, no events.
- **Surface**: both the REST API (play endpoint behaviour and its OpenAPI description) and the UI (player, tracks page).
- **Code**: `TrackController`, `TrackService`/`DefaultTrackService`, a new public `TrackMedia` type in the `track` package, `Tracks`/`DbTracks`; `Player.svelte`, the media-source store, `tracks/+page.svelte`. Tests: `TrackApiTest`/`AbstractApiTest` (Testcontainers) and a new Playwright spec under `app/e2e/`.
- **Docs**: README feature list (deep link / start at position).
- **Dependencies**: none added.
- **PR title**: `feat(track): stream byte ranges and start playback at a position` (branch `feat/range-streaming`).
