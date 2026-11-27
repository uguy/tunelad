# Design

## Context

- `POST /api/tracks` demands `title`, `description` and `setId`, and `DefaultTrackService.execute(AddTrackCommand)` rejects an unknown Set. The `track_tracks.title` and `description` columns are already nullable, and `TrackDTO.set` is already nullable (legacy Tracks), so storage needs no relaxing.
- `NewTrackSaved` is published inside the upload transaction; `identification` listens to it (`IdentificationOnUpload`, an `@ApplicationModuleListener`) after commit, on an executor thread. `identification` may already depend on `media`, `track::event` and `track::command`; `media` may depend only on `infrastructure`.
- `media` already runs ffmpeg for waveforms and renditions from a temp file written out of the stored audio (`RenditionMaker`), blocking work isolated on a scheduler.
- `UpdateTrackCommand` replaces all four fields and publishes `TrackUpdated`, which `search` projects and `live` announces as `track.indexed`.
- `IdentificationService.identify` already skips a Track with no Set.

## Goals / Non-Goals

**Goals:**
- Upload accepts a file alone and returns at once; tags arrive afterwards through the events that exist.
- A value an Account provided is never overwritten, even when a retried or redelivered event runs late.

**Non-Goals:**
- A tag-reading sidecar, or reading tags in the request path.
- Re-reading tags on demand, or choosing a Set later.

## Decisions

**1. Tags are read by `media`, applied by `identification`, stored by `track`.** `media` gains a public `MediaTags` interface (`Mono<AudioTags> read(String key)`, `AudioTags(title, artist, album, comment)`, every value nullable) implemented with ffprobe over a temp file, as `RenditionMaker` does with ffmpeg: blocking process on `boundedElastic`, temp file always deleted, a timeout. `identification` gets a second `NewTrackSaved` listener, `TagsOnUpload`, that calls `MediaTags` and then `TrackService.execute(ApplyTags)`. `track` owns the rule on what may be filled. No new module dependency is needed; the `media` and `identification` package-info files stay as they are, and `media`'s new interface sits in the module's base package, which is already exposed.
- *Alternative: read tags in `track` during upload.* Rejected: it makes the response wait on a process, and `track` may depend on `media` only for storage.
- *Alternative: a new `tags` module.* Rejected: it is one listener and one reader, and the proposal already files it under Identification.
- *Alternative: parse tags in Java (jaudiotagger).* Rejected: a new dependency that does not cover WMA and ogg as well as ffprobe, which is already a runtime requirement.

**2. ffprobe is configured beside ffmpeg.** `FfprobeProperties` (`tunelad.media.ffprobe.path`, default `ffprobe`, `timeout` default 30s) is separate from `FfmpegProperties`, so existing constructor calls in tests stay as they are. Output is `ffprobe -v error -print_format json -show_entries format_tags`; keys are lower-cased on read, because mp3, ogg and wma disagree on case. Tags are looked up in the format section, then in the first audio stream's tags (ogg and m4a vary). A missing executable, a non-zero exit or a timeout yields empty `AudioTags`, logged at warn; nothing fails.

**3. Provenance is a per-Track list of provided fields.** A new column `track_tracks.provided_fields TEXT[] NOT NULL DEFAULT '{}'` holds some of `title`, `description`, `artist`, `album`. Upload records each non-blank text part it received. `UpdateTrackCommand` records all four. The Liquibase changeset sets all four on existing rows, so a legacy Track never changes. `ApplyTags` fills a field only when it is not in the list and the tag is non-blank; a filled field is not added to the list, so it can still be corrected by a later edit, which then adds all four.
- *Alternative: guess from the title (file-name placeholder, equal to the file name).* Rejected: the file name is not stored, and a user can legitimately pick the same title.
- *Alternative: a single `metadata_edited` boolean.* Rejected: an uploader who typed only the title would block the artist tag too, against the spec.

**4. `ApplyTags` is applied under the row lock.** `ApplyTags(trackId, tags)` is a `TrackCommand` in `track::command`, handled like `ProposePerformances`: `findByIdForUpdate`, then write only the changed fields, so an owner's edit racing the listener either runs first (fields become provided and are kept) or second (overwrites the tags). It completes empty for a Track that no longer exists, saves and publishes `TrackUpdated` only when something changed (so a no-tags file or a redelivered event causes no `track.indexed`). The Artist goes through `Artists.findOrCreate`, so matching by trimmed, case-insensitive name is reused. A title is cut to 80 characters before it is stored; blank values count as absent.

**5. Title fallback is decided at upload, in the controller layer's command.** `AddTrackCommand.title` loses `@NotBlank`; the service stores `command.title()` or the file name without extension, cut to 80 characters, `Untitled` when empty. The `TrackController` passes `filePart.filename()`, strips any path, and treats blank text parts as absent. `setId` becomes optional: with none, no Performances are built and `performances` without a `setId` is a 400. The `track_tracks.title` column stays nullable, and `TrackDTO.title` keeps its `@NotBlank` because it is always set.

**6. `description` is optional on the edit too.** `TrackEdit` drops `@NotBlank` on `description`, `UpdateTrackCommand` likewise; a blank value is stored as null (as artist and album already are).

**7. REST contract.** `TrackDTO.description` becomes `@Schema(nullable = true)` and `NON_NULL` serialisation already omits it, so clients see it absent rather than `null`. The OpenAPI text of `POST /api/tracks` states that only `file` is required, the title fallback, that tags fill the rest, and that `performances` needs `setId`; its 400 description drops "title, description, setId". `PUT /api/tracks/{id}` states that only `title` is required. Both are flagged BREAKING for clients that assume a description.

**8. UI.**
- The `Track` type's `description` becomes optional; `TrackList` and the edit form omit empty lines and keep the labels for the fields that exist.
- The upload page puts the file first, marks the others "(optional)", adds a "No set yet" choice that is the default (it hides the Performance fields), and a short hint under the file field. It sends only filled fields. `uploadErrorMessage` drops the missing title/description wording; 400 now reads as a set or performances that do not fit.
- When tags arrive the list updates through the existing `refreshOnLive` on `track.indexed`; no new store.
- Optional status uses visible text "(optional)" inside the label, never colour alone; the required file field keeps `required`.

**9. Tests.** `TestAudio` gains tagged fixtures made with ffmpeg `-metadata` for mp3, m4a, ogg, flac and WMA, so no sample files are committed (commercial samples must stay out of the repo).

## Risks / Trade-offs

- [ffprobe missing on a host] → tags are skipped with a warning, the file-name title stays; the README names ffprobe next to ffmpeg, and the CI workflow's ffmpeg package already ships it.
- [Tag title placeholder visible for a moment] → the title first shows the file name, then the tag title via `track.indexed`; acceptable and stated in the specs.
- [Non-UTF-8 or junk tags] → values are trimmed, control characters stripped, title cut to 80 characters; descriptions are cut to the existing limit if one applies.
- [A Track with no Set cannot gain one before slice 3] → stated out of scope; the edit form does not offer it.
- [Breaking `description` absence] → flagged in the proposal and the OpenAPI text; the bundled UI is updated in the same change.
- [Race between the listener and an owner's edit] → the row lock and the provided list make either order safe, covered by a test.

## Migration Plan

1. The Liquibase changeset adds `provided_fields` and fills it for existing rows; it needs no downtime.
2. Deploy the backend and UI together (the UI tolerates a description that is present or absent).
3. Rollback: revert the release; the extra column is ignored by the previous version and can stay.
