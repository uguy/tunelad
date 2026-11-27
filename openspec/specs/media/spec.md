# media Specification

## Purpose
Play the stored audio of tracks: stream it through the REST API, whole or by byte range, and compute and serve each track's waveform. In the browser, a persistent player bar plays tracks with play/pause, previous/next, seeking (also by clicking a track's waveform) and volume, and lets listeners start a track at a given time from a link, and share such a link.

## Requirements

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

### Requirement: Serve a byte range of a track's audio
When a play request carries a `Range` header with a single byte range that overlaps the audio file, the system SHALL respond 206 Partial Content with exactly the requested bytes. The response SHALL carry `Content-Range: bytes <first>-<last>/<size>` and a `Content-Length` equal to the number of bytes sent. A range whose last position is past the end of the file SHALL be served up to the end of the file.

#### Scenario: Range in the middle of the file
- **GIVEN** a track has been uploaded with an mp3 file of N bytes, N greater than 1,500,000
- **WHEN** a client requests its audio with `Range: bytes=1000000-1499999`
- **THEN** the response status is 206 Partial Content with content type `audio/mpeg`
- **AND** `Content-Range` is `bytes 1000000-1499999/N` and `Content-Length` is 500000
- **AND** the body is byte-for-byte identical to bytes 1000000 to 1499999 of the uploaded file

#### Scenario: Open-ended range
- **GIVEN** a track has been uploaded with an mp3 file of N bytes
- **WHEN** a client requests its audio with `Range: bytes=S-` where S is less than N
- **THEN** the response status is 206 Partial Content
- **AND** `Content-Range` is `bytes S-(N-1)/N`
- **AND** the body is byte-for-byte identical to the uploaded file from byte S to its end

#### Scenario: Range from the start, as sent by a browser
- **GIVEN** a track has been uploaded with an mp3 file of N bytes
- **WHEN** a client requests its audio with `Range: bytes=0-`
- **THEN** the response status is 206 Partial Content with `Content-Range: bytes 0-(N-1)/N`
- **AND** the body is byte-for-byte identical to the uploaded file

#### Scenario: Suffix range
- **GIVEN** a track has been uploaded with an mp3 file of N bytes
- **WHEN** a client requests its audio with `Range: bytes=-1000`
- **THEN** the response status is 206 Partial Content
- **AND** `Content-Range` is `bytes (N-1000)-(N-1)/N`
- **AND** the body is byte-for-byte identical to the last 1000 bytes of the uploaded file

#### Scenario: Range ending past the end of the file
- **GIVEN** a track has been uploaded with an mp3 file of N bytes
- **WHEN** a client requests its audio with `Range: bytes=S-E` where S is less than N and E is N or more
- **THEN** the response status is 206 Partial Content
- **AND** `Content-Range` is `bytes S-(N-1)/N`

### Requirement: Reject an unsatisfiable byte range
When a play request carries a `Range` header with a single byte range that starts at or past the end of the audio file, the system SHALL respond 416 Range Not Satisfiable with `Content-Range: bytes */<size>` and no audio.

#### Scenario: Range starting past the end of the file
- **GIVEN** a track has been uploaded with an mp3 file of N bytes
- **WHEN** a client requests its audio with `Range: bytes=N-`
- **THEN** the response status is 416 Range Not Satisfiable
- **AND** `Content-Range` is `bytes */N`

#### Scenario: Unsatisfiable range on an unknown track
- **WHEN** a client requests `GET /api/tracks/{id}/play` with `Range: bytes=0-` and an id no track has
- **THEN** the response status is 404 Not Found

### Requirement: Ignore unsupported Range headers
When a play request carries a `Range` header that is malformed, uses a unit other than `bytes`, or lists more than one range, the system SHALL ignore it and respond as to a request without a `Range` header: status 200 with the whole file.

#### Scenario: Several ranges
- **GIVEN** a track has been uploaded with an mp3 file
- **WHEN** a client requests its audio with `Range: bytes=0-99,200-299`
- **THEN** the response status is 200 OK
- **AND** the body is byte-for-byte identical to the uploaded file

#### Scenario: Malformed range
- **GIVEN** a track has been uploaded with an mp3 file
- **WHEN** a client requests its audio with `Range: bytes=abc`
- **THEN** the response status is 200 OK
- **AND** the body is byte-for-byte identical to the uploaded file

### Requirement: Start playback at a position from a link
The UI SHALL accept a link to the tracks page of the form `/tracks?play=<id>&t=<seconds>`. Opening it SHALL load the player with the track that has that id, positioned at the given number of seconds. When `t` is missing, not a number or negative, the player SHALL start at the beginning of the track.

#### Scenario: Open a link with a start time
- **GIVEN** a track with id X exists
- **WHEN** a user opens `/tracks?play=X&t=90`
- **THEN** the player loads the audio of track X
- **AND** playback is positioned at 90 seconds

#### Scenario: Open a link without a start time
- **GIVEN** a track with id X exists
- **WHEN** a user opens `/tracks?play=X`
- **THEN** the player loads the audio of track X, positioned at the beginning

#### Scenario: Open a link with an invalid start time
- **GIVEN** a track with id X exists
- **WHEN** a user opens `/tracks?play=X&t=abc`
- **THEN** the player loads the audio of track X, positioned at the beginning

### Requirement: Copy a link to the current playback position
While a track is loaded in the player, the UI SHALL offer a control that copies to the clipboard an absolute link of the form `/tracks?play=<id>&t=<seconds>` for that track, where `<seconds>` is the current playback position rounded down to a whole second. The control SHALL not be offered when no track is loaded.

#### Scenario: Copy a link while a track is playing
- **GIVEN** track X is loaded in the player and its playback position is 95.7 seconds
- **WHEN** the user activates the "copy link at current time" control
- **THEN** the clipboard contains the absolute URL of `/tracks?play=X&t=95`

#### Scenario: No track loaded
- **WHEN** no track has been loaded in the player
- **THEN** the "copy link at current time" control is not offered

### Requirement: Play without logging in
The system SHALL serve `GET /api/tracks/{id}/play`, with or without a `Range` header, to anonymous clients exactly as to authenticated ones, so that links to a track work for anyone.

#### Scenario: Anonymous playback
- **GIVEN** a track has been uploaded with an mp3 file
- **WHEN** a client without a session or token requests `GET /api/tracks/{id}/play` with `Range: bytes=0-`
- **THEN** the response has a success status and content type `audio/mpeg`

#### Scenario: Anonymous link with a start time
- **GIVEN** nobody is logged in in the browser
- **WHEN** a visitor opens `/tracks?play=X&t=95` for an existing track X
- **THEN** the player loads track X at 95 seconds, as for a logged-in user

### Requirement: Get the waveform of a track
The system SHALL serve the waveform of the track with the given id on `GET /api/tracks/{id}/waveform` as JSON with a `status` and a `peaks` array. When `status` is `READY`, `peaks` SHALL hold exactly 200 numbers from 0 to 1, in playback order, and the loudest peak SHALL be 1 unless the track is silent. When `status` is `PENDING` or `FAILED`, `peaks` SHALL be empty. The system SHALL respond 404 Not Found when no track has that id.

#### Scenario: Waveform of a processed track
- **GIVEN** a track has been uploaded and its waveform has been computed
- **WHEN** a client requests `GET /api/tracks/{id}/waveform`
- **THEN** the response status is 200 OK with content type `application/json`
- **AND** `status` is `READY` and `peaks` holds 200 numbers, each from 0 to 1, the largest being 1

#### Scenario: Peaks follow the loudness of the audio
- **GIVEN** a track has been uploaded with a wav file whose first half is silent and whose second half is a loud tone
- **WHEN** its waveform has been computed
- **THEN** the first 90 peaks are below 0.05 and the last 90 peaks are above 0.5

#### Scenario: Waveform not computed yet
- **GIVEN** a track has been uploaded and its waveform is still being computed
- **WHEN** a client requests `GET /api/tracks/{id}/waveform`
- **THEN** the response status is 200 OK with `status` `PENDING` and an empty `peaks` array

#### Scenario: Waveform of an unknown track
- **WHEN** a client requests `GET /api/tracks/{id}/waveform` with an id no track has
- **THEN** the response status is 404 Not Found

### Requirement: Compute a track's waveform after upload
After a track's audio has been stored, the system SHALL compute its waveform without delaying the upload response, so that `GET /api/tracks/{id}/waveform` reports `READY` within a few seconds for every supported format (mp3, m4a, ogg, flac, wav). When the audio cannot be decoded, the waveform SHALL become `FAILED` and the track SHALL still play.

#### Scenario: Waveform ready shortly after upload
- **GIVEN** a track is uploaded with an mp3 file
- **WHEN** a client polls `GET /api/tracks/{id}/waveform` for up to 10 seconds
- **THEN** the waveform reaches status `READY`

#### Scenario: Every supported format gets a waveform
- **GIVEN** tracks are uploaded with an m4a, an ogg, a flac and a wav file
- **WHEN** a client polls their waveforms for up to 10 seconds
- **THEN** each waveform reaches status `READY` with 200 peaks

#### Scenario: Undecodable audio
- **GIVEN** a track is uploaded with a file named `noise.mp3` whose content is random bytes
- **WHEN** a client polls its waveform for up to 10 seconds
- **THEN** the waveform reaches status `FAILED`
- **AND** `GET /api/tracks/{id}/play` still returns the uploaded bytes

### Requirement: Backfill waveforms of tracks stored before waveforms existed
When the application starts, the system SHALL compute the waveform of every stored audio file that has none, so tracks uploaded before waveforms existed get one without being uploaded again.

#### Scenario: Track stored without a waveform
- **GIVEN** audio is stored for a track and no waveform exists for it
- **WHEN** the application starts
- **THEN** that track's waveform reaches status `READY` within a few seconds

### Requirement: Delete a track's waveform with its audio
When a track's audio is deleted, the system SHALL delete its waveform too.

#### Scenario: Waveform gone after deleting the track
- **GIVEN** a track has a `READY` waveform
- **WHEN** its owner deletes the track
- **THEN** `GET /api/tracks/{id}/waveform` responds 404 Not Found

### Requirement: Get a waveform without logging in
The system SHALL serve `GET /api/tracks/{id}/waveform` to anonymous clients exactly as to authenticated ones.

#### Scenario: Anonymous waveform
- **GIVEN** a track has a `READY` waveform
- **WHEN** a client without a session or token requests `GET /api/tracks/{id}/waveform`
- **THEN** the response status is 200 OK with status `READY`

### Requirement: Persistent player bar
The UI SHALL show a player bar fixed to the bottom of the viewport once a track has been loaded. It SHALL show that track's title and owner, its elapsed time and duration, and SHALL keep playing and stay visible while the user navigates between pages of the app. No player bar SHALL be shown before a track is loaded.

#### Scenario: Player bar appears when a track is played
- **GIVEN** the tracks page lists "Knees Up" owned by "Alice Liddell" and no track is loaded
- **WHEN** the user plays "Knees Up"
- **THEN** a player bar appears at the bottom of the viewport showing "Knees Up" and "Alice Liddell"
- **AND** it shows the elapsed time and the duration of the track

#### Scenario: Playback continues across pages
- **GIVEN** "Knees Up" is playing
- **WHEN** the user navigates to the home page
- **THEN** the player bar is still shown and the playback position keeps advancing

#### Scenario: No player bar before playing
- **WHEN** a user opens the tracks page without a `play` parameter
- **THEN** no player bar is shown

### Requirement: Play and pause
The player bar and each track row SHALL offer a play/pause control. Activating a row's control SHALL play that track, or pause and resume it when it is the loaded track. Every control SHALL reflect whether the loaded track is playing, with an accessible name that says the action it performs.

#### Scenario: Pause from the player bar
- **GIVEN** "Knees Up" is playing
- **WHEN** the user activates the player bar's pause control
- **THEN** playback pauses and both the player bar control and the "Knees Up" row control are labelled as play controls

#### Scenario: Resume from the track row
- **GIVEN** "Knees Up" is loaded and paused at 30 seconds
- **WHEN** the user activates the "Knees Up" row's play control
- **THEN** playback resumes from 30 seconds

#### Scenario: Switch tracks from the list
- **GIVEN** "Knees Up" is playing
- **WHEN** the user activates the play control of another track "Reel"
- **THEN** the player loads "Reel" from the beginning and plays it

### Requirement: Previous and next track
The player SHALL keep, as its queue, the list of tracks shown when a track was started from a row. The player bar SHALL offer next and previous controls that load the adjacent track of that queue. Next SHALL be disabled on the last track. Previous SHALL restart the current track when its position is past 3 seconds, and load the previous track otherwise. When a track ends, the next one SHALL play.

#### Scenario: Next track
- **GIVEN** the list shows "Knees Up" then "Reel" and the user played "Knees Up"
- **WHEN** the user activates the next control
- **THEN** the player loads and plays "Reel"

#### Scenario: Previous restarts a track that has progressed
- **GIVEN** "Reel" is playing at 20 seconds and "Knees Up" comes before it in the queue
- **WHEN** the user activates the previous control
- **THEN** "Reel" restarts from the beginning

#### Scenario: Track ends
- **GIVEN** the list shows "Knees Up" then "Reel" and "Knees Up" is playing
- **WHEN** "Knees Up" reaches its end
- **THEN** the player loads and plays "Reel"

#### Scenario: Track opened from a link has no queue
- **WHEN** a user opens `/tracks?play=X`
- **THEN** the player loads track X and the next control is disabled

### Requirement: Seek within the track
The player bar SHALL offer a progress control showing the playback position, which the user can drag or click to seek. Each track row SHALL show the track's waveform: the played part is highlighted for the loaded track, and clicking a point seeks there, or starts a track that is not loaded at that point. A waveform not `READY` SHALL show a flat placeholder that seeks the same way.

#### Scenario: Seek with the progress control
- **GIVEN** a 200-second track is playing
- **WHEN** the user sets the player bar's progress control to its middle
- **THEN** playback continues from about 100 seconds

#### Scenario: Seek by clicking the waveform of the loaded track
- **GIVEN** a 200-second track "Knees Up" is playing
- **WHEN** the user clicks the "Knees Up" waveform at three quarters of its width
- **THEN** playback continues from about 150 seconds
- **AND** the first three quarters of the waveform are highlighted as played

#### Scenario: Start another track from its waveform
- **GIVEN** "Knees Up" is playing and "Reel" is a 100-second track
- **WHEN** the user clicks the "Reel" waveform at half its width
- **THEN** the player loads "Reel" and plays it from about 50 seconds

#### Scenario: Waveform still pending
- **GIVEN** the waveform of "Reel" is `PENDING`
- **WHEN** the tracks page is shown
- **THEN** the "Reel" row shows a flat placeholder bar in place of the waveform

### Requirement: Volume and mute
The player bar SHALL offer a volume control and a mute toggle. The chosen volume and mute state SHALL be kept in the browser and applied again on the next visit.

#### Scenario: Mute
- **GIVEN** a track is playing
- **WHEN** the user activates the mute toggle
- **THEN** the audio is muted and the toggle is labelled as an unmute control

#### Scenario: Volume remembered
- **GIVEN** the user set the volume to 30%
- **WHEN** the user reloads the app and plays a track
- **THEN** the volume is 30%

### Requirement: Announce a computed waveform
When a track's waveform becomes `READY` or `FAILED`, the system SHALL raise the library event `waveform.computed` with
that track's id on the live stream. It SHALL NOT raise it while the waveform stays `PENDING` (for example while
ffmpeg is missing).

#### Scenario: Waveform ready
- **GIVEN** a client is connected to `GET /api/live`
- **WHEN** the waveform of track 42 becomes `READY`
- **THEN** the client receives `waveform.computed` with id `42`

#### Scenario: Waveform failed
- **GIVEN** a client is connected to `GET /api/live`
- **WHEN** the audio of track 42 cannot be decoded and its waveform becomes `FAILED`
- **THEN** the client receives `waveform.computed` with id `42`

#### Scenario: Waveform stays pending
- **GIVEN** a client is connected to `GET /api/live` and ffmpeg is not installed
- **WHEN** the waveform computation of track 42 is attempted
- **THEN** the client receives no `waveform.computed` for track 42

### Requirement: Show a waveform as soon as it is computed
The UI SHALL replace a track's waveform placeholder with its waveform as soon as the system announces it computed,
without a reload and however long after the upload that happens. The UI SHALL NOT poll for waveforms.

#### Scenario: Waveform appears after an upload
- **GIVEN** alice is on the tracks page
- **WHEN** she uploads a track and its waveform is computed a few seconds later
- **THEN** that track's row shows its waveform within 2 seconds of the computation
- **AND** the page was not reloaded

#### Scenario: Waveform computed long after the list was shown
- **GIVEN** the tracks page shows track 42 with a placeholder because its waveform is `PENDING`
- **WHEN** the waveform is computed 5 minutes later, for example by the startup backfill after ffmpeg was installed
- **THEN** the placeholder is replaced by the waveform

#### Scenario: Waveform for a track that is not on screen
- **GIVEN** the tracks page is open and track 42 is far below the visible rows
- **WHEN** the waveform of track 42 is computed
- **THEN** the UI does not fetch it before the row nears the viewport

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
