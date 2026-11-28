## ADDED Requirements

### Requirement: An Artist is a Musician, a Band or unclassified
The system SHALL give every Artist a `kind` that is `musician`, `band` or absent (unclassified). An Artist created from a Track's `artist` name, or existing before this requirement, SHALL be unclassified. Any authenticated Account SHALL be able to set an Artist's kind with `PUT /api/artists/{id}` (body `{ "kind": "musician" | "band" | null }`), responding 200 with the Artist, or 404 when it does not exist. An unauthenticated request SHALL get 401. An Artist that has members SHALL NOT be changed to `musician` or to unclassified: the response SHALL be 409 Conflict.

#### Scenario: Classify as a Band
- **GIVEN** an unclassified Artist "Altan"
- **WHEN** an authenticated client sets its kind to `band`
- **THEN** the response is 200 and the Artist has kind `band`

#### Scenario: Unknown Artist
- **WHEN** an authenticated client sets the kind of an Artist id that does not exist
- **THEN** the response is 404

#### Scenario: Unauthenticated
- **WHEN** a client without credentials sets an Artist's kind
- **THEN** the response is 401

#### Scenario: Band with members cannot become a Musician
- **GIVEN** a Band with one member
- **WHEN** an authenticated client sets its kind to `musician`
- **THEN** the response is 409 and the kind is unchanged

### Requirement: A Band has Musicians as members
The system SHALL let any authenticated Account replace the members of a Band with `PUT /api/artists/{id}/members` (body `{ "members": ["<name>", ...] }`). Each name SHALL resolve like an Artist name (matched ignoring case and surrounding whitespace, created on first use) and the resolved Artist SHALL become, or stay, a Musician. A name that matches an Artist that is a Band SHALL be rejected with 409. Blank names are ignored and duplicates count once; the Band's own name is rejected with 400. Only a Band has members: the request on a Musician or unclassified Artist SHALL be 409. `GET /api/artists/{id}` SHALL return the Artist with its `kind` and, for a Band, its `members` (id and name each), ordered by name. A Musician SHALL list the Bands it belongs to as `bands`.

#### Scenario: Add members by name
- **GIVEN** a Band "Altan" and no Artist "Mairéad Ní Mhaonaigh"
- **WHEN** an authenticated client sets its members to ["Mairéad Ní Mhaonaigh"]
- **THEN** the Artist "Mairéad Ní Mhaonaigh" exists with kind `musician`
- **AND** fetching "Altan" lists it as a member

#### Scenario: Existing unclassified Artist becomes a Musician
- **GIVEN** an unclassified Artist "Paul Brock" and a Band "Dervish"
- **WHEN** an authenticated client sets the members of "Dervish" to ["  paul brock "]
- **THEN** "Paul Brock" has kind `musician` and is the only member

#### Scenario: Replacing members
- **GIVEN** a Band with members "A" and "B"
- **WHEN** an authenticated client sets its members to ["B", "C"]
- **THEN** its members are "B" and "C"; "A" remains a Musician

#### Scenario: A Band cannot be a member
- **GIVEN** two Bands "Altan" and "Dervish"
- **WHEN** an authenticated client sets the members of "Altan" to ["Dervish"]
- **THEN** the response is 409 and the members are unchanged

#### Scenario: Members of a non-Band
- **GIVEN** a Musician
- **WHEN** an authenticated client sets its members
- **THEN** the response is 409

#### Scenario: A Musician lists its Bands
- **GIVEN** "Paul Brock" is a member of "Dervish"
- **WHEN** a client fetches "Paul Brock"
- **THEN** `bands` contains "Dervish"

### Requirement: A Performance exposes its Artist
Each `TunePerformance` returned by `GET /api/tunes/{id}/performances` SHALL carry `artistId` and `artistKind` of its Track's Artist, both absent when the Track has no Artist, in addition to the existing `artist` name.

#### Scenario: Performance of a Band's Track
- **GIVEN** a Track by the Band "Altan" with a Performance of "The Kesh"
- **WHEN** a client lists the performances of "The Kesh"
- **THEN** the entry has `artist` "Altan", that Artist's `artistId` and `artistKind` `band`
