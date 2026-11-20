# Spec Delta

## MODIFIED Requirements

### Requirement: Play a track
The system SHALL stream the audio file of the track with the given id on `GET /api/tracks/{id}/play`, with the content type of the track's format (`audio/mpeg` for audio stored without a known format), whether or not the request carries a `Range` header, and respond 404 Not Found when no track has that id. A response carrying the whole file SHALL have status 200 and state the file size in `Content-Length`. Every successful response SHALL carry `Accept-Ranges: bytes`.

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

#### Scenario: Audio stored before formats were recorded
- **GIVEN** audio was stored for a track before the content type of its format was recorded with it
- **WHEN** a client requests `GET /api/tracks/{id}/play` for that track
- **THEN** the response has a success status and content type `audio/mpeg`

#### Scenario: Play an unknown track
- **WHEN** a client requests `GET /api/tracks/{id}/play` with an id no track has
- **THEN** the response status is 404 Not Found
