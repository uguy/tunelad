## ADDED Requirements

### Requirement: Tune, Set and Performance events reach every connection
The system SHALL announce, with the following events, that something concerning Tunes, Sets or Performances changed.
Each SHALL reach every connection, anonymous or authenticated, on every running instance, because they concern
public data, and SHALL NOT carry content:
- `tune.changed`, whose `id` is the Tune's, when a Tune is created by hand or imported from thesession.org. A bulk import of thesession.org tunes announces one `tune.changed` without `id` when it ends.
- `set.changed`, whose `id` is the Set's, when a Set is created.
- `performance.changed`, whose `id` is the Track's, when the Performances of a Track change: the Track is added, its Performances edited, proposed by identification or taken from an attached Set, or the Track is deleted.

#### Scenario: Tune created
- **GIVEN** an anonymous client and a client logged in as bob are connected
- **WHEN** alice creates Tune 12
- **THEN** both receive `tune.changed` with id `12`

#### Scenario: Bulk import ends
- **GIVEN** a client is connected
- **WHEN** an Administrator's bulk import of thesession.org tunes ends
- **THEN** the client receives one `tune.changed` without an id

#### Scenario: Set created
- **GIVEN** a client is connected
- **WHEN** alice creates Set 7
- **THEN** the client receives `set.changed` with id `7`

#### Scenario: Performance edited
- **GIVEN** a client is connected
- **WHEN** alice edits the key of a Performance of Track 42
- **THEN** the client receives `performance.changed` with id `42`

#### Scenario: Track deleted
- **GIVEN** Track 42 has Performances and a client is connected
- **WHEN** Track 42 is deleted
- **THEN** the client receives `performance.changed` with id `42`

#### Scenario: No content in the event
- **WHEN** alice creates a Tune named "The Kesh"
- **THEN** the event data is `{"type":"tune.changed","id":"<id>"}` and contains no name

### Requirement: The UI follows Tunes, Sets and Performances live
The UI SHALL refetch what it shows when it receives `tune.changed` (Tunes list and Tune page), `set.changed` (Sets list
and Set page) or `performance.changed` (the Performances on a Tune page), and after its live connection reopens.

#### Scenario: Tune created elsewhere
- **GIVEN** the Tunes list is open in one browser
- **WHEN** another Account creates a Tune
- **THEN** the first list shows the new Tune without a reload

#### Scenario: Set created elsewhere
- **GIVEN** the Sets list is open in one browser
- **WHEN** another Account creates a Set
- **THEN** the first list shows the new Set without a reload

#### Scenario: Performance added elsewhere
- **GIVEN** a Tune page is open in one browser
- **WHEN** another Account adds a Track whose Set contains that Tune
- **THEN** the first page lists the new Performance without a reload

#### Scenario: Reconnect refreshes
- **GIVEN** the Tunes list is open and its live connection drops
- **AND** another Account creates a Tune while it is down
- **WHEN** the connection reopens
- **THEN** the list includes the new Tune without a reload
