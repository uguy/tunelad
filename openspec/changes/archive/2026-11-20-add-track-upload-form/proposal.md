# Proposal

## Why

The UI can browse, play, edit and delete tracks, but uploading one still means using curl or Swagger UI. That is the
main way content gets into the app, so logged-in users need an upload form. The current API also gets in the way of a
good form. It answers an upload with an empty body, so the UI can't show the new track before search catches up. It
also stores any file, mp3 or not, so a track that can't be played can get into the library. Finally, it has no size
limit, and a failed upload can leave its audio behind in GridFS.

## What Changes

- **UI**: a new page, `/tracks/upload`, with a form for title, description, artist, album and the audio file. The
  tracks page links to it for logged-in users, and shows anonymous visitors a link to log in instead. While the file
  uploads, the page shows a progress bar and a Cancel control. On success it goes back to `/tracks`, where the new
  track is shown right away. When an upload fails or is cancelled, the form keeps every field and the chosen file,
  explains what went wrong, and lets the user try again.
- **BREAKING**: `POST /api/tracks` answers **201 Created** with the stored track as its JSON body and a `Location`
  header (`/api/tracks/{id}`), instead of 200 with an empty body. A client that checks for exactly 200 has to accept
  201.
- Uploads accept the **mp3, m4a (AAC), ogg, flac and wav** formats, identified by the file name's extension. Any other
  file is refused with **415 Unsupported Media Type**, and nothing is stored. The track's `format` field gets the
  values `MP3`, `M4A`, `OGG`, `FLAC` and `WAV`.
- Audio files larger than **100 MB** are refused with **413 Content Too Large**, and nothing is stored.
- A blank `title` or `description` is refused with 400, as a missing one already is.
- An upload that fails or is aborted partway through leaves no track and no audio behind.
- `GET /api/tracks/{id}/play` serves each track's audio with the content type of its format (for example
  `audio/flac`). Audio stored before this change keeps being served as `audio/mpeg`.

Affected Modulith modules: `track` (upload endpoint, validation, formats, cleanup after a failed upload, UI) and
`media` (stores and serves each file's content type, through its public `MediaStore` API). `search` only shows the new
`format` values, with no behaviour change. The change touches both the REST API and the UI.

Proposed PR title: `feat(track)!: add an upload form with progress and more audio formats`

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `track`: the upload answers 201 with the stored track, accepts five audio formats, refuses unsupported formats
  (415), files over 100 MB (413) and blank required fields (400), and leaves nothing behind when it fails. New UI
  requirements cover the upload page, its link on the tracks page, progress and cancel, and failure handling.
- `media`: playback is served with the content type of the stored audio's format instead of always `audio/mpeg`.

## Impact

- **REST API**: `POST /api/tracks` documents a 201 response with a `TrackDTO` schema and a `Location` header, plus 413
  and 415 responses. The 400 description now also covers blank values. The `TrackDTO.format` enum gets new values.
  `GET /api/tracks/{id}/play` lists the five audio content types it can produce.
- **Backend**: `TrackFormat`, `TrackController`, `TrackService`/`DefaultTrackService` (upload returns the track, checks
  run before anything is written, cleanup on error and cancel), `MediaStore` and `GridFsMediaStore` (content type in
  the GridFS metadata), `MediaController` (content type per file), and the multipart size limit in `application.yml`.
- **UI**: new route `app/src/routes/tracks/upload/`, the upload method in `Tracks.ts` (`XMLHttpRequest` for progress
  events and abort), and an upload link on the tracks page.
- **Tests**: API tests with Testcontainers for each upload outcome and for content types on play, store tests for
  content type metadata and cleanup, and Playwright e2e tests for the upload page.
- **Docs**: the features list in README.adoc.

## Out of scope

- Tags on upload (the `Track` document has them, but uploads don't take them)
- Uploading several files at once, drag and drop, or a queue of uploads
- Detecting the format from the file's content (magic bytes) rather than its extension
- Transcoding, or reading metadata such as title and artist from the file's own tags
- Keeping a draft of the form across a login redirect: a user whose session has expired logs in again and starts over
- Resumable or chunked uploads
- Replacing a track's audio file
- A configurable size limit per user or role
