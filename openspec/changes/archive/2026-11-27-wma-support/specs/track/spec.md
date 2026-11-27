# Spec Delta

## MODIFIED Requirements

### Requirement: Accepted audio formats
The system SHALL accept uploads whose file name ends in `.mp3`, `.m4a`, `.ogg`, `.flac`, `.wav` or `.wma`, case-insensitively, and store the track with format `MP3`, `M4A`, `OGG`, `FLAC`, `WAV` or `WMA` respectively. It SHALL refuse any other file with 415 Unsupported Media Type and store neither a track nor audio. The upload form SHALL offer and accept the same formats, and refuse any other file before sending it.

#### Scenario: Upload each supported format
- **WHEN** an authenticated client uploads a file named `tune.mp3`, `tune.m4a`, `tune.ogg`, `tune.flac`, `tune.wav` or `tune.wma` with a title and description
- **THEN** the response status is 201 Created
- **AND** the stored track's format is `MP3`, `M4A`, `OGG`, `FLAC`, `WAV` or `WMA` respectively

#### Scenario: Extension in upper case
- **WHEN** an authenticated client uploads a file named `TUNE.FLAC` or `TUNE.WMA` with a title and description
- **THEN** the response status is 201 Created and the stored track's format is `FLAC` or `WMA` respectively

#### Scenario: Unsupported file
- **WHEN** an authenticated client uploads a file named `notes.txt`, or a file name without an extension, with a title and description
- **THEN** the response status is 415 Unsupported Media Type
- **AND** listing tracks returns no track with that title

#### Scenario: A WMA file is chosen in the upload form
- **GIVEN** an authenticated user is on the upload form
- **WHEN** they choose a file named `tune.wma`
- **THEN** the form does not refuse the file
- **AND** submitting the form with a title, description and set uploads the track

#### Scenario: A WMA track appears in the list
- **GIVEN** a track was uploaded with a WMA file
- **WHEN** a client lists tracks
- **THEN** the track is listed with format `WMA`
