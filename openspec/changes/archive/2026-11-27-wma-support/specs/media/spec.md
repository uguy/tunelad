# Spec Delta

## MODIFIED Requirements

### Requirement: Play a track
The system SHALL stream the audio file of the track with the given id on `GET /api/tracks/{id}/play`, with the content type of the track's format (`audio/mpeg` for audio stored without a known format), whether or not the request carries a `Range` header, and respond 404 Not Found when no track has that id. For a format a browser cannot play (`WMA`), the audio streamed SHALL be the track's playable rendition, with content type `audio/mp4`, and every range and size SHALL refer to that rendition. A response carrying the whole file SHALL have status 200 and state the file size in `Content-Length`. Every successful response SHALL carry `Accept-Ranges: bytes`.

#### Scenario: Play an uploaded track
- **GIVEN** a track has been uploaded with an mp3 file
- **WHEN** a client requests `GET /api/tracks/{id}/play` for that track
- **THEN** the response has a success status and content type `audio/mpeg`
- **AND** the body is byte-for-byte identical to the uploaded file

#### Scenario: Play without a Range header
- **GIVEN** a track has been uploaded with an mp3 file
- **WHEN** a client requests `GET /api/tracks/{id}/play` for that track without a `Range` header
- **THEN** the response status is 200 OK with content type `audio/mpeg` and `Accept-Ranges: bytes`
- **AND** `Content-Length` equals the size of the uploaded file
- **AND** the body is byte-for-byte identical to the uploaded file

#### Scenario: Play each supported format
- **GIVEN** tracks have been uploaded with an m4a, an ogg, a flac and a wav file
- **WHEN** a client requests `GET /api/tracks/{id}/play` for each of them, with and without `Range: bytes=0-`
- **THEN** the content type is `audio/mp4` (M4A), `audio/ogg` (OGG), `audio/flac` (FLAC) and `audio/wav` (WAV) respectively
- **AND** each body is byte-for-byte identical to its uploaded file (or the requested range of it)

#### Scenario: Play a WMA track
- **GIVEN** a track has been uploaded with a wma file and its audio is `READY`
- **WHEN** a client requests `GET /api/tracks/{id}/play` for that track, with and without `Range: bytes=0-`
- **THEN** the content type is `audio/mp4`
- **AND** `Content-Length`, `Content-Range` and the body describe the rendition, not the uploaded wma file
- **AND** the body is audio an AAC decoder reads

#### Scenario: Audio stored before formats were recorded
- **GIVEN** audio was stored for a track before the content type of its format was recorded with it
- **WHEN** a client requests `GET /api/tracks/{id}/play` for that track
- **THEN** the response has a success status and content type `audio/mpeg`

#### Scenario: Play an unknown track
- **WHEN** a client requests `GET /api/tracks/{id}/play` with an id no track has
- **THEN** the response status is 404 Not Found

## ADDED Requirements

### Requirement: Prepare a playable rendition of audio browsers cannot play
After the audio of a track in a format a browser cannot play (`WMA`) has been stored, the system SHALL make an AAC rendition of it in the background, without delaying the upload response. The uploaded file SHALL be kept unchanged, and the track's waveform and identification SHALL keep reading it. Formats a browser plays SHALL get no rendition.

#### Scenario: Upload returns before the rendition is ready
- **GIVEN** the conversion is slow
- **WHEN** an authenticated client uploads a track with a wma file
- **THEN** the response status is 201 Created
- **AND** the track's audio is `PENDING` until the conversion ends

#### Scenario: Rendition ready shortly after upload
- **GIVEN** a track was uploaded with a wma file of a few seconds
- **WHEN** a client polls `GET /api/tracks/{id}/audio` for up to 10 seconds
- **THEN** the audio reaches status `READY`

#### Scenario: Waveform of a WMA track
- **GIVEN** a track was uploaded with a wma file
- **WHEN** a client polls `GET /api/tracks/{id}/waveform` for up to 10 seconds
- **THEN** the waveform reaches status `READY` with 200 peaks

#### Scenario: Formats a browser plays are not converted
- **GIVEN** a track was uploaded with an mp3 file
- **WHEN** a client requests `GET /api/tracks/{id}/audio`
- **THEN** the audio status is `READY` at once

### Requirement: Get the audio state of a track
The system SHALL serve the state of the playable audio of the track with the given id on `GET /api/tracks/{id}/audio`, as a JSON object with a `status`: `PENDING` while its rendition is being made, `READY` when `/play` can serve it (always, for formats a browser plays, and for audio stored before renditions existed), `FAILED` when the audio could not be converted. It SHALL respond 404 Not Found when no track has that id, and require no login.

#### Scenario: Audio being converted
- **GIVEN** a track was uploaded with a wma file and its rendition is not made yet
- **WHEN** a client requests `GET /api/tracks/{id}/audio`
- **THEN** the response is 200 with status `PENDING`

#### Scenario: Audio that cannot be converted
- **GIVEN** a track was uploaded with a file named `broken.wma` whose bytes are not audio
- **WHEN** a client polls `GET /api/tracks/{id}/audio` for up to 10 seconds
- **THEN** the status reaches `FAILED`

#### Scenario: Audio of an unknown track
- **WHEN** a client requests `GET /api/tracks/{id}/audio` with an id no track has
- **THEN** the response status is 404 Not Found

#### Scenario: Audio state without logging in
- **GIVEN** an anonymous client
- **WHEN** it requests `GET /api/tracks/{id}/audio` for an existing track
- **THEN** the response status is 200

### Requirement: Playback of audio that is not ready
While the playable audio of a track is not `READY`, `GET /api/tracks/{id}/play` SHALL NOT stream the uploaded file: it SHALL respond 503 Service Unavailable with a `Retry-After` header when the status is `PENDING`, and 422 Unprocessable Content when it is `FAILED`. Neither response SHALL have an audio body.

#### Scenario: Play while the rendition is being made
- **GIVEN** a track was uploaded with a wma file and its audio is `PENDING`
- **WHEN** a client requests `GET /api/tracks/{id}/play`
- **THEN** the response status is 503 Service Unavailable with a `Retry-After` header

#### Scenario: Play audio that could not be converted
- **GIVEN** the audio of a track is `FAILED`
- **WHEN** a client requests `GET /api/tracks/{id}/play`
- **THEN** the response status is 422 Unprocessable Content

#### Scenario: A failed conversion does not block the track
- **GIVEN** the audio of a track is `FAILED`
- **WHEN** its owner edits or deletes the track
- **THEN** the edit or deletion succeeds as for any other track

### Requirement: Backfill renditions that are pending
When the application starts, the system SHALL make, in the background and one at a time, the rendition of every stored audio that needs one and has none, such as audio stored while ffmpeg was missing. Startup SHALL NOT wait for it.

#### Scenario: ffmpeg installed after the upload
- **GIVEN** a track was uploaded with a wma file while ffmpeg was missing, so its audio stayed `PENDING`
- **WHEN** the application is restarted with ffmpeg installed
- **THEN** the audio of the track becomes `READY` without a new upload

### Requirement: Delete a rendition with its audio
When a track is deleted, the system SHALL delete its rendition together with its uploaded audio.

#### Scenario: Track with a rendition deleted
- **GIVEN** a track uploaded with a wma file whose audio is `READY`
- **WHEN** its owner deletes the track
- **THEN** `GET /api/tracks/{id}/play` and `GET /api/tracks/{id}/audio` respond 404 Not Found
- **AND** no audio of that track remains stored

### Requirement: Announce prepared audio
When a track's audio becomes `READY` or `FAILED` after being `PENDING`, the system SHALL raise the library event `audio.prepared` with that track's id on the live stream. It SHALL NOT raise it for audio that is `READY` from the start, nor while the audio stays `PENDING` (for example while ffmpeg is missing).

#### Scenario: Rendition ready
- **GIVEN** a client is connected to `GET /api/live`
- **WHEN** the rendition of track 42 is made and its audio becomes `READY`
- **THEN** the client receives `audio.prepared` with id `42`

#### Scenario: Conversion failed
- **GIVEN** a client is connected to `GET /api/live`
- **WHEN** the audio of track 42 cannot be converted and becomes `FAILED`
- **THEN** the client receives `audio.prepared` with id `42`

#### Scenario: Audio stays pending
- **GIVEN** a client is connected to `GET /api/live` and ffmpeg is not installed
- **WHEN** the conversion of track 42 is attempted
- **THEN** the client receives no `audio.prepared` for track 42

#### Scenario: Audio that needs no conversion
- **GIVEN** a client is connected to `GET /api/live`
- **WHEN** a track is uploaded with an mp3 file
- **THEN** the client receives no `audio.prepared` for it

### Requirement: Show the state of audio that is being prepared
The UI SHALL show, on a track whose audio is `PENDING`, that it is being prepared, with a text status and not by colour alone, and SHALL NOT offer to play it. When the system announces it prepared, the UI SHALL make the track playable without a reload or polling. On a track whose audio is `FAILED`, the UI SHALL say so in text, offer no play action, and keep editing and deleting available.

#### Scenario: Preparing then playable
- **GIVEN** alice uploads a wma track and is on the tracks page
- **WHEN** the system announces `audio.prepared` for it
- **THEN** the track's "preparing" status is replaced by a play action within 2 seconds
- **AND** the page was not reloaded

#### Scenario: Preparing is announced to assistive technology
- **GIVEN** a track's audio is `PENDING`
- **WHEN** the tracks page shows it
- **THEN** its row contains the text "Preparing audio" next to a disabled play control

#### Scenario: Conversion failed in the UI
- **GIVEN** a track's audio is `FAILED`
- **WHEN** the tracks page shows it
- **THEN** its row says that the audio could not be prepared
- **AND** it has no play action
- **AND** its owner can still edit and delete it
