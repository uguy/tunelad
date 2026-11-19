# track Specification

## Purpose
Manage the track library through the REST API: upload a track with its audio file and metadata, browse and fetch tracks, play their audio (whole or by byte range), and clear the library; and let listeners in the browser player start a track at a given time from a link, and share such a link.

## Requirements

### Requirement: Upload a track
The system SHALL accept a multipart upload on `POST /api/tracks` with the parts `title`, `description` and `file` (the audio file), and the optional parts `artist` and `album`, store the track with its audio, and respond with a success status.

#### Scenario: Upload with all metadata
- **WHEN** a client uploads an mp3 file with a title, description, artist and album
- **THEN** the response has a success status
- **AND** listing tracks returns a track with that title, description, artist and album, and format `MP3`

#### Scenario: Upload without optional metadata
- **WHEN** a client uploads an mp3 file with only a title and description
- **THEN** the response has a success status
- **AND** the stored track has that title and description, and no artist or album

#### Scenario: Required part missing
- **WHEN** a client uploads a track without a `title`, a `description` or a `file` part
- **THEN** the response status is 400 Bad Request
- **AND** no track is stored

### Requirement: Assign a unique identifier to each track
The system SHALL give every stored track a unique `id`, which identifies it in all other track endpoints.

#### Scenario: Two uploads with the same title
- **WHEN** a client uploads two tracks with the same title
- **THEN** listing tracks returns two tracks with different ids

### Requirement: List tracks
The system SHALL return all stored tracks as a JSON array on `GET /api/tracks`, each with its `id`, `title`, `description`, `artist`, `album` and `format`.

#### Scenario: Library with tracks
- **GIVEN** two tracks have been uploaded
- **WHEN** a client requests `GET /api/tracks`
- **THEN** the response is a JSON array containing both tracks with their metadata

#### Scenario: Empty library
- **GIVEN** no track has been uploaded
- **WHEN** a client requests `GET /api/tracks`
- **THEN** the response is an empty JSON array

### Requirement: Get a track by id
The system SHALL return the track with the given id as a JSON object on `GET /api/tracks/{id}`, and respond 404 Not Found when no track has that id.

#### Scenario: Existing track
- **GIVEN** a track has been uploaded
- **WHEN** a client requests `GET /api/tracks/{id}` with that track's id
- **THEN** the response status is 200 OK
- **AND** the body is that track with its metadata

#### Scenario: Unknown id
- **WHEN** a client requests `GET /api/tracks/{id}` with an id no track has
- **THEN** the response status is 404 Not Found

### Requirement: Play a track
The system SHALL stream the audio file of the track with the given id on `GET /api/tracks/{id}/play`, with content type `audio/mpeg`, whether or not the request carries a `Range` header, and respond 404 Not Found when no track has that id. A response carrying the whole file SHALL have status 200 and state the file size in `Content-Length`. Every successful response SHALL carry `Accept-Ranges: bytes`.

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

### Requirement: Delete all tracks
The system SHALL delete every track and its audio on `DELETE /api/tracks` and respond with a success status.

#### Scenario: Clear a populated library
- **GIVEN** tracks have been uploaded
- **WHEN** a client requests `DELETE /api/tracks`
- **THEN** the response has a success status
- **AND** listing tracks returns an empty JSON array
- **AND** getting or playing any previously uploaded track responds 404 Not Found
