# Spec Delta

## ADDED Requirements

### Requirement: Announce tracks that become searchable
When a track's search copy has been written (a new track, or an edit of an existing one), the system SHALL raise the
library event `track.indexed` with that track's id on the live stream, so that a client refetching the list or a
search then finds the track as it is now. It SHALL NOT raise it before the copy is written.

#### Scenario: New track becomes searchable
- **GIVEN** a client is connected to `GET /api/live`
- **WHEN** alice uploads a track and it becomes searchable
- **THEN** the client receives `track.indexed` with that track's id
- **AND** a search made on receiving it includes the track

#### Scenario: Edited track
- **GIVEN** a client is connected to `GET /api/live`
- **WHEN** a track's title is edited and its search copy is replaced
- **THEN** the client receives `track.indexed` with that track's id
- **AND** a search for the new title made on receiving it includes the track

### Requirement: Announce tracks that stop being searchable
When a track's search copy has been removed because the track was deleted, the system SHALL raise the library event
`track.unindexed` with that track's id on the live stream.

#### Scenario: Deleted track
- **GIVEN** a client is connected to `GET /api/live`
- **WHEN** a track is deleted and its search copy is removed
- **THEN** the client receives `track.unindexed` with that track's id
- **AND** a search made on receiving it does not include the track

### Requirement: The UI list follows what becomes searchable
The tracks list shown in the UI SHALL be refreshed when `track.indexed` or `track.unindexed` is received, using the
search text currently entered, so that the list always matches what the search would return.

#### Scenario: Refresh keeps the active search
- **GIVEN** the tracks page shows the results for the search text "rain"
- **WHEN** a track titled "Rain on tin" is indexed
- **THEN** the list is refetched with the search text "rain"
- **AND** it now includes "Rain on tin"

#### Scenario: Indexed track outside the active search
- **GIVEN** the tracks page shows the results for the search text "rain"
- **WHEN** a track titled "Sunny day" is indexed
- **THEN** the list is refetched and does not include "Sunny day"
