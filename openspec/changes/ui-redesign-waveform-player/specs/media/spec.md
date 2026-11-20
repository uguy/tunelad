# Spec Delta

## ADDED Requirements

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
- **WHEN** the user navigates to the about page
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
