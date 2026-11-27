Issue: #837

# Proposal

## Why

Many traditional-music recordings are Windows Media Audio (`.wma`) files, which Tune Lad refuses with 415. Browsers cannot
play WMA, so accepting the file as-is would give a Track that uploads fine and plays silence. WMA support must therefore
include a playable rendition. This is also the first step towards a file-only upload (the audio file is the only
required field), where users drop in whatever audio they own.

## What Changes

- `POST /api/tracks` accepts `.wma` files (`audio/x-ms-wma`) as a new Track format.
- The original file is stored unchanged. At upload, ffmpeg makes an AAC/m4a rendition from it, off the reactive request
  path, stored next to the original.
- `GET /api/tracks/{id}/play` serves the rendition for a WMA Track, with content type `audio/mp4` and the usual `Range`
  and `Content-Length` behaviour computed on the rendition. Formats browsers already play are served as before,
  byte-for-byte.
- `TrackFormat` separates the format a Track is stored as from the content type it is played as.
- The rendition's state (`PENDING`, `READY`, `FAILED`) is served by the `media` module next to the waveform, on
  `GET /api/tracks/{id}/audio`, and announced by a new `audio.prepared` live event. While it is not ready, `/play`
  answers with a retryable status rather than silence, and the UI shows "preparing" and refreshes on the event; a failed
  transcode is shown as such and the Track stays deletable and editable. Pending renditions are backfilled at startup,
  as waveforms are.
- Identification, the waveform and any other analysis keep reading the original, which ffmpeg decodes as it is.
- The upload form's accepted-file list and client-side check include `.wma`.
- WMA test audio is generated with ffmpeg at test time. No third-party recordings are committed.

No existing REST contract is **BREAKING**: the new format is additive, and a Track in a format browsers play behaves
exactly as before.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `track`: upload accepts WMA (accepted formats, in the API and the upload form).
- `media`: playback serves a rendition for WMA Tracks; the "byte-for-byte identical to the uploaded file" rule applies to
  formats browsers play; the audio state endpoint, the startup backfill and the UI's preparing state are defined.
- `live`: `audio.prepared` joins the library events.

## Impact

- **Modulith modules**: `track` (`TrackFormat`, upload validation), `media` (rendition computation next to the existing
  ffmpeg peak extraction, play endpoint, audio state endpoint, a new `media::event` type), `live` (bridges the new event;
  it already depends on `media::event`). `identification` is unchanged. No `package-info.java` dependency changes; the
  rendition is triggered by the existing `AudioStored` event.
- **REST API / OpenAPI**: `.wma` on the upload `file` part (415 text updated), new `GET /api/tracks/{id}/audio`, and
  documented 503 and 422 responses on `/play`. The OpenAPI description changes accordingly.
- **UI**: yes. The accepted-files list and `checkFile` in `app/src/routes/tracks/Tracks.ts`, plus a preparing/failed
  indicator in the player and track list, refreshed by `refreshOnLive`. Theme tokens only, accessible status text.
- **Database**: a Liquibase changeset for the rendition's state; audio stored earlier needs none and counts as ready.
- **Runtime dependency**: ffmpeg is already required by the waveform; the transcode reuses its configuration
  (`FfmpegProperties`) and must run on a bounded elastic scheduler.
- **Storage**: a WMA Track stores two blobs, the original and the rendition. Deleting the Track removes both.
- **Tests**: Testcontainers integration test with an ffmpeg-generated WMA; a Playwright test for the upload and the
  preparing state with mocked API.
- **Proposed PR title**: `feat(media): support wma uploads through an aac rendition`.

## Out of scope

- Reading tags (title, artist, album) from the file, and making title, description and Set optional: the next slice.
- Open-ended recognition that derives the Set from the audio: a later slice.
- Other non-browser formats (AIFF, APE, ...); the rendition mechanism should allow them later, but none is added now.
- Transcoding formats browsers already play.
- Lazy or on-first-play transcoding, and backfilling renditions for existing Tracks (none are WMA).
- Offering the original WMA for download.
