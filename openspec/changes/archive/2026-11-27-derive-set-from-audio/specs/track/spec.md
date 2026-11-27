# Spec Delta

## MODIFIED Requirements

### Requirement: A track may have no Set
The system SHALL treat a track without a Set like any other track: it is listed, fetched, edited, played and deleted as usual, its `set` is absent and its `performances` is empty. It MAY be given a Set by Identification, once, after which it is a track with a Set. The UI SHALL show a track without a Set with no Tunes section, and a track that is given a Set shows it and its Performances once it is fetched again, without a reload of the page.

#### Scenario: Fetch a track without a Set
- **GIVEN** a track was uploaded with only a file
- **WHEN** a client requests `GET /api/tracks/{id}`
- **THEN** the response status is 200 OK, the body has no `set` and its `performances` is empty

#### Scenario: Play, edit and delete a track without a Set
- **GIVEN** alice uploaded a track with only a file
- **WHEN** a client plays it and alice edits its title and then deletes it
- **THEN** each request succeeds as for a track with a Set

#### Scenario: A track without a Set in the list
- **GIVEN** the tracks page lists a track that has no Set, no description and no artist
- **WHEN** the track's details are opened
- **THEN** no Tunes section and no empty artist, album or description lines are shown

#### Scenario: A Set arrives while the details are open
- **GIVEN** the tracks page shows the open details of a track without a Set
- **WHEN** Identification gives the track the Set of "Cooley's" then "The Kesh"
- **THEN** the details show a Tunes section with those two Tunes and their start offsets, without a reload

#### Scenario: A Set is given once
- **GIVEN** a track that Identification gave the Set of "Cooley's"
- **WHEN** a later analysis recognises "The Kesh"
- **THEN** the track keeps the Set of "Cooley's" and its Performances
