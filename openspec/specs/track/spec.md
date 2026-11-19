# track Specification

## Purpose
Manage the track library through the REST API: upload a track with its audio file and metadata, and browse and fetch tracks. Playing the stored audio is covered by the `media` capability.

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
