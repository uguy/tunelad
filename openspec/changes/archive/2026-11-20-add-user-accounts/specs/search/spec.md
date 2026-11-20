# Spec Delta

## MODIFIED Requirements

### Requirement: Uploaded tracks become searchable shortly after upload
After an upload of a track completes successfully, the system SHALL include that track in matching search results within 5 seconds, with the same fields as the track list. Search results are eventually consistent: a search made earlier than that is not required to contain the new track.

#### Scenario: New track found after a short delay
- **GIVEN** no track titled "Knees Up" exists
- **WHEN** a client uploads a track titled "Knees Up" and the upload succeeds
- **THEN** within 5 seconds, a search for `knees` returns a response containing "Knees Up"

#### Scenario: Searchable track has the track list fields
- **GIVEN** a track titled "Knees Up" with an artist and an album has been uploaded
- **WHEN** a search for `knees` returns it
- **THEN** its `id`, `title`, `description`, `artist`, `album`, `format` and `owner` equal those of the same track in `GET /api/tracks`

## ADDED Requirements

### Requirement: Search without logging in
The system SHALL serve `GET /api/tracks/search` to anonymous clients exactly as to authenticated ones.

#### Scenario: Anonymous search
- **GIVEN** a track titled "Knees Up" has been uploaded and is searchable
- **WHEN** a client without a session or token searches for `knees`
- **THEN** the response status is 200 OK and contains "Knees Up"
