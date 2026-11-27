# Spec Delta

## ADDED Requirements

### Requirement: Tracks without a description are searchable
The system SHALL index a track that has no description, no artist, no album or no Set by the words of the title and description it does have, and SHALL return it from a search like any other track.

#### Scenario: Track with only a title
- **GIVEN** a track was uploaded as `rainfall.mp3` with only a file
- **WHEN** a client searches for "rainfall"
- **THEN** the result contains that track, with a null or absent description

### Requirement: Metadata read from tags reaches search
When the metadata of a track is filled from the tags of its file, the system SHALL update the track in search shortly after and announce it as for any other change, so that a search finds the track by its tag title and description.

#### Scenario: Searching by a tag title
- **GIVEN** a track was uploaded as `track01.mp3` with only a file, and its tag title is "Rainfall"
- **WHEN** its tags have been read and a client searches for "rainfall"
- **THEN** the result contains that track, titled "Rainfall"
- **AND** a search for "track01" no longer finds it

#### Scenario: The list updates by itself
- **GIVEN** a client is connected to `GET /api/live` after uploading a file whose tag title is "Rainfall"
- **WHEN** the tags have been read
- **THEN** the client receives `track.indexed` with that track's id
