# Spec Delta

## ADDED Requirements

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
