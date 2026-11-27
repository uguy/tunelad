## ADDED Requirements

### Requirement: A Performance carries a Transcription
The system SHALL let a Performance hold an optional Transcription, ABC notation of at most 20000 characters, returned as `abc` on every Performance of a Track and `null` when it has none. `PUT /api/tracks/{id}` SHALL replace each Performance's `abc` together with its start offset, key and notes: an omitted or blank `abc` clears it, and one over the limit SHALL answer 400 Bad Request and change nothing.

#### Scenario: Write a Transcription
- **GIVEN** alice has uploaded a Track of the Set "The Kesh"
- **WHEN** alice puts a Performance with `abc` "X:1\nK:G\nGABc|" to `/api/tracks/{id}`
- **THEN** the response status is 200 OK and the Performance has that `abc`
- **AND** `GET /api/tracks/{id}` returns the same `abc`

#### Scenario: Clear a Transcription
- **GIVEN** a Performance has a Transcription
- **WHEN** alice puts the Performance without `abc`
- **THEN** the Performance's `abc` is null

#### Scenario: Transcription too long
- **WHEN** alice puts a Performance with an `abc` of 20001 characters
- **THEN** the response status is 400 Bad Request
- **AND** the Performance keeps its previous `abc`

### Requirement: List the Performances of a Tune
The system SHALL answer `GET /api/tunes/{id}/performances`, to anonymous clients too, with every Performance of that Tune across Tracks, each with its Track id and title, the Track's Artist, `position`, `key`, `notes` and `abc`, ordered by Track title. A Tune with no Performance SHALL answer 200 OK with an empty list.

#### Scenario: Performances across Tracks
- **GIVEN** two Tracks each contain the Tune "The Kesh"
- **WHEN** a client requests `GET /api/tunes/{id}/performances`
- **THEN** the body has two Performances, one per Track, with their Track titles

#### Scenario: Tune never recorded
- **WHEN** a client requests the Performances of a Tune no Track contains
- **THEN** the response status is 200 OK and the list is empty
