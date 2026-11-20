# search Specification

## Purpose
Find tracks by free text through the REST API, so listeners can narrow the library down by words from a track's title or description.

## Requirements

### Requirement: Search tracks by words in title or description
The system SHALL return, on `GET /api/tracks/search?q=<text>`, the tracks whose title or description contains at least one of the words in the query, as a JSON array of tracks with the same fields as the track list. Matching SHALL be case-insensitive.

#### Scenario: Match on a title word
- **GIVEN** tracks titled "Knees Up" and "Mossy Banks" have been uploaded
- **WHEN** a client searches for `knees`
- **THEN** the response contains "Knees Up" and not "Mossy Banks"

#### Scenario: Match on a description word
- **GIVEN** a track whose description mentions "Breabach" and another whose description does not
- **WHEN** a client searches for `breabach`
- **THEN** the response contains only the track whose description mentions "Breabach"

#### Scenario: Case-insensitive match
- **GIVEN** a track titled "Knees Up" has been uploaded
- **WHEN** a client searches for `KNEES`
- **THEN** the response contains "Knees Up"

#### Scenario: Any query word matches
- **GIVEN** tracks titled "Knees Up" and "Mossy Banks" have been uploaded
- **WHEN** a client searches for `knees mossy`
- **THEN** the response contains both tracks

### Requirement: Match whole words, including word variants
The system SHALL match query words against whole words and their grammatical variants (for example singular and plural), not against arbitrary substrings.

#### Scenario: Word variant matches
- **GIVEN** a track titled "Knees Up" has been uploaded
- **WHEN** a client searches for `knee`
- **THEN** the response contains "Knees Up"

#### Scenario: Partial word does not match
- **GIVEN** a track titled "Knees Up" has been uploaded
- **WHEN** a client searches for `kne`
- **THEN** the response does not contain "Knees Up"

### Requirement: Order search results by relevance
The system SHALL order search results so that tracks matching more of the query words come before tracks matching fewer.

#### Scenario: Better match first
- **GIVEN** a track whose title and description both match the query words, and a track that matches only one of them
- **WHEN** a client searches with those words
- **THEN** the better-matching track comes first in the response

### Requirement: Blank query returns every track
The system SHALL return all stored tracks when the search query is empty or contains only whitespace.

#### Scenario: Empty query
- **GIVEN** two tracks have been uploaded
- **WHEN** a client requests `GET /api/tracks/search?q=`
- **THEN** the response contains both tracks

#### Scenario: Whitespace-only query
- **GIVEN** two tracks have been uploaded
- **WHEN** a client searches for a query made only of spaces
- **THEN** the response contains both tracks

### Requirement: No match returns an empty result
The system SHALL return an empty JSON array when no track matches the query.

#### Scenario: Unmatched word
- **GIVEN** tracks have been uploaded
- **WHEN** a client searches for a word that appears in no title or description
- **THEN** the response is an empty JSON array

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

### Requirement: Search without logging in
The system SHALL serve `GET /api/tracks/search` to anonymous clients exactly as to authenticated ones.

#### Scenario: Anonymous search
- **GIVEN** a track titled "Knees Up" has been uploaded and is searchable
- **WHEN** a client without a session or token searches for `knees`
- **THEN** the response status is 200 OK and contains "Knees Up"

### Requirement: Edits and deletions reach search shortly after
After a track edit or deletion completes successfully, the system SHALL reflect it in search results within 5 seconds:
an edited track SHALL be matched on its new metadata and returned with it, and a deleted track SHALL no longer be
returned. Searches made earlier than that are not required to reflect the change.

#### Scenario: Edited track found by its new title
- **GIVEN** a searchable track titled "Knees Up"
- **WHEN** its owner edits the title to "Hornpipe Set" and the edit succeeds
- **THEN** within 5 seconds, a search for `hornpipe` returns that track with the title "Hornpipe Set"
- **AND** a search for `knees` no longer returns it

#### Scenario: Edited track has the track list fields
- **GIVEN** a searchable track whose metadata has just been edited
- **WHEN** a search returns it after the edit has reached search
- **THEN** its `id`, `title`, `description`, `artist`, `album`, `format` and `owner` equal those of the same track in `GET /api/tracks`

#### Scenario: Deleted track disappears from search
- **GIVEN** searchable tracks titled "Knees Up" and "Mossy Banks"
- **WHEN** the owner of "Knees Up" deletes it and the deletion succeeds
- **THEN** within 5 seconds, a blank search returns "Mossy Banks" and not "Knees Up"
