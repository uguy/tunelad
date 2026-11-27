# set Specification

## Purpose
Sets of Tunes: one shared Set per distinct ordered list of Tunes, created, listed and viewed through the API and the UI.

## Requirements

### Requirement: One Set per ordered list of Tunes
An authenticated Account SHALL create a Set with `POST /api/sets` giving `tuneIds`, an ordered list of one to twenty existing Tune ids
(a Tune may repeat). There SHALL be one Set per distinct ordered list: an existing Set is returned with 200 OK, whoever created it, and
a new one with 201 Created. Two simultaneous identical requests SHALL end with one Set.

#### Scenario: Create a Set
- **GIVEN** alice is authenticated and the Tunes "Cooley's" and "The Kesh" exist
- **WHEN** alice sends `POST /api/sets` with those Tune ids in that order
- **THEN** the response status is 201 Created
- **AND** the Set lists "Cooley's" then "The Kesh"

#### Scenario: The same list gives the same Set
- **GIVEN** alice created the Set of "Cooley's" then "The Kesh"
- **WHEN** bob sends `POST /api/sets` with the same Tune ids in the same order
- **THEN** the response status is 200 OK
- **AND** the Set has the same id, and only one Set exists

#### Scenario: Another order is another Set
- **GIVEN** the Set of "Cooley's" then "The Kesh" exists
- **WHEN** alice creates the Set of "The Kesh" then "Cooley's"
- **THEN** the response status is 201 Created
- **AND** two Sets exist

#### Scenario: A Set of one Tune
- **WHEN** alice creates a Set from the single Tune "Cooley's"
- **THEN** the response status is 201 Created

### Requirement: Invalid Set requests
An empty list, more than twenty Tunes or an unknown Tune id SHALL answer 400 Bad Request and create nothing. An unauthenticated
`POST /api/sets` SHALL answer 401 Unauthorized.

#### Scenario: Invalid lists
- **WHEN** alice sends an empty list, or a list with an unknown Tune id
- **THEN** the response status is 400 Bad Request
- **AND** no Set is created

#### Scenario: Anonymous request
- **WHEN** an unauthenticated client sends `POST /api/sets`
- **THEN** the response status is 401 Unauthorized

### Requirement: Browse Sets
The system SHALL list Sets with `GET /api/sets` and return one with `GET /api/sets/{id}` (404 when it does not exist). A Set carries
its id and its Tunes in order, each with id, name and Dance type. The list is ordered by the names of the Tunes, optionally filtered by `tuneId`
to the Sets that contain that Tune. Reading is open to anonymous visitors, like Tunes.

#### Scenario: List the Sets of a Tune
- **GIVEN** the Sets (Cooley's, The Kesh) and (The Kesh)
- **WHEN** a client sends `GET /api/sets?tuneId=<Cooley's id>`
- **THEN** only the first Set is returned

#### Scenario: Unknown Set
- **WHEN** a client sends `GET /api/sets/unknown`
- **THEN** the response status is 404 Not Found

### Requirement: Sets in the UI
The UI SHALL offer a Sets page listing Sets with their Tunes in order, a Set page listing its Tunes in order with links to the Tune pages,
and a builder where an authenticated Account searches Tunes, adds them to the list, moves them up or down with buttons, removes them, and
creates the Set. When the Set already existed the UI SHALL go to it and say so. The builder SHALL be operable with the keyboard, announce
list changes to assistive technology, and use the theme tokens.

#### Scenario: Build a Set
- **GIVEN** alice is on the builder
- **WHEN** she adds "The Kesh" then "Cooley's", moves "Cooley's" up, and creates the Set
- **THEN** she lands on the page of the Set listing "Cooley's" then "The Kesh"

#### Scenario: The Set already existed
- **GIVEN** the Set alice builds exists already
- **WHEN** she creates it
- **THEN** she lands on that Set's page and a message says it already existed
