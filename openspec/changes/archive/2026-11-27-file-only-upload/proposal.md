Issue: #839

# Proposal

## Why

Uploading a Track today means typing a title and a description and choosing or building a Set before the app takes the
file, although the file already says most of it: its tags carry the title, the Artist and the album. The app is hard to
use for its main flow, which is to drop in a recording and let Tune Lad work out what it can. The audio file must be the
only required field, and everything else is optional or guessed. This is slice 1 of that flow; recognising the Tunes to
derive the Set is a later slice.

## What Changes

- `POST /api/tracks` requires only the `file` part. `title`, `description`, `setId`, `artist`, `album` and `performances`
  are optional.
- A Track always has a title: the uploader's if given, otherwise the file name without its extension, at once. Shortly
  after the upload, the file's tags (title, artist, album, comment) fill every field the uploader left blank: the title
  replaces the file-name placeholder, the comment becomes the description. The change is announced by the existing live
  updates, so the UI shows it without a reload.
- A value an Account provided, at upload or in a later edit, is never overwritten by tags, and a tag never replaces a
  value already there.
- A Track may have no Set (`setId` omitted): it has no Performances and is not identified. A Track stored before Sets
  existed is already such a Track. Choosing a Set later is out of scope.
- The description becomes optional on a Track and in an edit; the title stays required in an edit.
- Tags are read from the stored audio in the background, for every supported format including WMA, so the upload response
  never waits for it. A file with no tags, or whose tags cannot be read, keeps the file-name title and stays valid.
- The upload form asks for the file first, marks every other field optional, offers "no set yet" in the Set field, and
  says that Tune Lad fills in what it can from the file. The tracks list updates when the tags arrive.
- `GLOSSARY.md`: a Track records a Set when it has one; Identification also covers reading the file's tags.

**Breaking REST API (flagged)**: a Track's `description` may now be null, which a client assuming a non-null description
must handle. Requests that sent all fields still work unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `track`: the upload requirements (only the file is required, title fallback, no Set), the edit requirements
  (description optional), the Track representation (nullable description), and the upload form.
- `identification`: reading the file's tags and filling blank fields, never overwriting a value an Account provided.
- `search`: a Track with no description or Set is indexed and searchable.

## Impact

- **Modulith modules**: `track` (upload command and validation, a command to fill blank fields, Track fields recording
  what an Account provided), `identification` (a listener that reads tags after upload and calls `track` through its
  command API), `media` (reading tags from stored audio with ffprobe next to the existing ffmpeg use; exact placement
  decided in design.md), `search` (nullable fields in the projection). `live` already announces `track.indexed`.
  `allowedDependencies` changes, if any, are listed in design.md.
- **REST API / OpenAPI**: relaxed required parts on `POST /api/tracks` and nullable `description`; the OpenAPI description
  changes accordingly.
- **UI**: yes. The upload form, the tracks list (a Track with no description, no artist or no Set), and the track edit
  form. Theme tokens only, accessible optional-field labelling.
- **Database**: a Liquibase changeset recording which fields an Account provided (`description` is already a nullable column).
- **Runtime dependency**: ffprobe, installed with ffmpeg, so CI and the README already cover it.
- **Tests**: Testcontainers integration tests with ffmpeg-generated audio that carries tags (mp3, m4a, WMA); Playwright
  tests for the form with API mocked.
- **Proposed PR title**: `feat(track): upload a track from its audio file alone`.

## Out of scope

- Recognising the Tunes in the audio to derive and create the Set: slice 3.
- Choosing or changing a Track's Set after upload.
- Cover art, writing tags back to files, and online lookups such as MusicBrainz.
- Bulk or multi-file upload.
- Changing how Identification places the start of each Performance.
- Re-reading tags on demand (a "refresh from file" action).
