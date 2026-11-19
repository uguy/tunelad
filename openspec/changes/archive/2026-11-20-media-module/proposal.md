# Proposal

## Why

The `track` module currently owns three things:

- the track library (metadata);
- the stored audio (GridFS);
- playback: `GET /api/tracks/{id}/play` with byte-range handling, and the player behaviour built on it.

Playback goes through `TrackService` as a `PlayTrackCommand`, even though it is a query that only
needs the stored audio. Six of the ten `track` requirements are about playing audio.

Moving the stored audio and playback into a dedicated `media` module splits the library from the
media. It mirrors the previous search change: there the module owned its own data and reacted to
events. Here `track` writes to `media` through a synchronous, published API, which is Spring
Modulith's other way for modules to work together.

## What Changes

- New `media` Modulith module that owns:
  - the audio files in GridFS;
  - a public API that `track` calls to store an upload's audio;
  - `GET /api/tracks/{id}/play`, with its byte-range handling (200, 206, 416).
- Audio is keyed by the track id, exactly as stored today (the GridFS filename is the track id).
  Media treats it as an opaque key and never calls `track`. Existing stored audio keeps playing,
  and no data migration is needed.
- Uploading a track still stores its metadata and its audio in one request. `track` saves the
  metadata, asks `media` to store the audio, then publishes `NewTrackSaved`, in that order, as
  today.
- `track` loses everything about playback and audio storage: `PlayTrackCommand`, the play
  operation of `TrackService`, `TrackMedia`, and the GridFS code. `track` now depends on `media`.
  `media` depends on nothing but `infrastructure`.
- The play endpoint keeps its path, headers, status codes and body. In the OpenAPI description,
  it moves from the `Tracks` tag to a new `Media` tag. The REST API is not **BREAKING**.
- The player behaviours (start playback at a position from a link, copy a link at the current
  position) move to the `media` capability with the play requirements. Neither their behaviour nor
  the frontend code changes.

Affected Modulith modules: `track` (playback and audio storage removed, calls `media` on upload)
and `media` (new). REST API: not breaking. Only the OpenAPI tag of the play operation changes.
UI: no change.

## Capabilities

### New Capabilities

- `media`: serving a track's stored audio over the REST API, including byte ranges, and the
  browser player behaviours built on it (start at a position from a link, copy a link at the
  current position). These requirements move from `track` without any change in behaviour.

### Modified Capabilities

- `track`: removes the six requirements that move to `media`: "Play a track", "Serve a byte range
  of a track's audio", "Reject an unsatisfiable byte range", "Ignore unsupported Range headers",
  "Start playback at a position from a link", and "Copy a link to the current playback position".
  Upload, listing, get-by-id and identifier requirements stay.

## Impact

- **Code**:
  - New in `org.tunelad.media`:
    - the public storage API;
    - the GridFS storage and byte-range reads, moved from `DbTracks`;
    - the play controller, moved from `TrackController`.
  - `track` changes:
    - `TrackService`, `DefaultTrackService`, `Tracks`, `DbTracks` and `TrackController` lose
      playback and audio storage;
    - `PlayTrackCommand` and `TrackMedia` are deleted;
    - `track/package-info.java` adds `media` to its allowed dependencies.
- **Tests**:
  - The play and range tests move from `TrackApiTest` to a media API test, one test per spec
    scenario as now. Upload tests stay in `track`.
  - `ModularApplicationTests` checks the new dependency direction.
- **OpenAPI**: the `/api/tracks/{id}/play` operation moves to the `Media` tag. Its path,
  parameters, responses and headers are unchanged.
- **Data**: none. The same GridFS bucket and file naming are kept.
- **Dependencies**: none.

## Out of scope

- Changing the play URL, response format or range behaviour.
- Giving audio its own identifier separate from the track id, or allowing several audio files per
  track.
- Storing or serving a content type per file (play keeps answering `audio/mpeg`).
- Making audio storage part of the MongoDB transaction (GridFS stays outside it, as today), and
  cleaning up audio orphaned by a failed upload.
- Deleting audio (there is no delete feature).
- Moving frontend code (`Player.svelte` stays under `routes/tracks`).

## PR title

`refactor(media): move audio storage and play into a media module`
