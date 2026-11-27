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
search text of the results being shown (not text typed and not yet searched), so that the list always matches what
that search would return. A refresh that fails SHALL leave the list as it is.

#### Scenario: Refresh keeps the active search
- **GIVEN** the tracks page shows the results for the search text "rain"
- **WHEN** a track titled "Rain on tin" is indexed
- **THEN** the list is refetched with the search text "rain"
- **AND** it now includes "Rain on tin"

#### Scenario: Typed but not searched text is not used
- **GIVEN** the tracks page shows the results for the search text "rain"
- **AND** the visitor has typed "sun" in the search box without searching
- **WHEN** a track titled "Rain on tin" is indexed
- **THEN** the list is refetched with the search text "rain"

#### Scenario: A failed refresh keeps the list
- **GIVEN** the tracks page shows tracks
- **WHEN** a track is indexed and the refetch of the list fails
- **THEN** the list still shows the same tracks

#### Scenario: Indexed track outside the active search
- **GIVEN** the tracks page shows the results for the search text "rain"
- **WHEN** a track titled "Sunny day" is indexed
- **THEN** the list is refetched and does not include "Sunny day"

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
