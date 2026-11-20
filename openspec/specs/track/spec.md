# track Specification

## Purpose
Manage the track library through the REST API: upload a track with its audio file and metadata, and browse and fetch tracks. Playing the stored audio is covered by the `media` capability.

## Requirements

### Requirement: Upload a track
The system SHALL accept a multipart upload on `POST /api/tracks` from an authenticated user, with the parts `title`, `description` and `file` (the audio file), and the optional parts `artist` and `album`, store the track with its audio and the uploader as its owner, and respond with a success status.

#### Scenario: Upload with all metadata
- **WHEN** an authenticated client uploads an mp3 file with a title, description, artist and album
- **THEN** the response has a success status
- **AND** listing tracks returns a track with that title, description, artist and album, and format `MP3`

#### Scenario: Upload without optional metadata
- **WHEN** an authenticated client uploads an mp3 file with only a title and description
- **THEN** the response has a success status
- **AND** the stored track has that title and description, and no artist or album

#### Scenario: Required part missing
- **WHEN** an authenticated client uploads a track without a `title`, a `description` or a `file` part
- **THEN** the response status is 400 Bad Request
- **AND** no track is stored

### Requirement: Assign a unique identifier to each track
The system SHALL give every stored track a unique `id`, which identifies it in all other track endpoints.

#### Scenario: Two uploads with the same title
- **WHEN** a client uploads two tracks with the same title
- **THEN** listing tracks returns two tracks with different ids

### Requirement: List tracks
The system SHALL return all stored tracks as a JSON array on `GET /api/tracks`, each with its `id`, `title`, `description`, `artist`, `album`, `format` and `owner`.

#### Scenario: Library with tracks
- **GIVEN** two tracks have been uploaded
- **WHEN** a client requests `GET /api/tracks`
- **THEN** the response is a JSON array containing both tracks with their metadata and owner

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

### Requirement: Uploading requires authentication
The system SHALL reject a track upload that carries neither a valid session nor a valid bearer token with 401 Unauthorized, without redirecting, and SHALL store nothing.

#### Scenario: Anonymous upload
- **WHEN** a client without a session or token uploads an mp3 file with a title and description
- **THEN** the response status is 401 Unauthorized and the response is not a redirect
- **AND** listing tracks returns no track with that title

### Requirement: Every track has an owner
The system SHALL record the account that uploaded a track as its owner, and SHALL include it in every track it returns as `owner`, an object with the account's `id` and `displayName` at upload time.

#### Scenario: Owner is the uploader
- **GIVEN** alice's account has id A and display name "Alice Liddell"
- **WHEN** alice uploads a track
- **THEN** that track in `GET /api/tracks` has `owner.id` A and `owner.displayName` "Alice Liddell"

#### Scenario: Owner on a single track
- **GIVEN** alice has uploaded a track
- **WHEN** a client requests `GET /api/tracks/{id}` for that track
- **THEN** the body has alice's account as `owner`

#### Scenario: Owners of different uploaders
- **GIVEN** alice and bob have each uploaded a track
- **WHEN** a client lists tracks
- **THEN** alice's track has alice as owner and bob's track has bob as owner

### Requirement: Browse tracks without logging in
The system SHALL serve `GET /api/tracks` and `GET /api/tracks/{id}` to anonymous clients exactly as to authenticated ones.

#### Scenario: Anonymous list
- **GIVEN** a track has been uploaded
- **WHEN** a client without a session or token requests `GET /api/tracks`
- **THEN** the response status is 200 OK and contains that track

#### Scenario: Anonymous get
- **GIVEN** a track has been uploaded
- **WHEN** a client without a session or token requests `GET /api/tracks/{id}` for it
- **THEN** the response status is 200 OK

### Requirement: Show who uploaded each track
The UI track list SHALL show, for each track, the display name of its owner.

#### Scenario: Uploader shown in the list
- **GIVEN** the track list contains "Knees Up" owned by "Alice Liddell"
- **WHEN** a visitor opens the tracks page
- **THEN** the "Knees Up" entry shows "Alice Liddell" as its uploader
