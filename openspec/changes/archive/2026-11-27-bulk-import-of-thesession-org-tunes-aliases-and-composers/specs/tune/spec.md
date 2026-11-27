## MODIFIED Requirements

### Requirement: Import a Tune from thesession.org
The system SHALL let an authenticated Account import a Tune with `POST /api/tunes/import` giving a thesession.org
tune id. The Tune SHALL take its name, aliases, Dance type, usual key and Reference transcription (a complete ABC tune) from
thesession.org and keep the thesession.org id; the single-Tune API publishes no author, so the author stays empty. Importing an id that was already imported SHALL answer 200 OK with the
existing Tune and create nothing. An id unknown to thesession.org SHALL answer 404 Not Found, and an unreachable
thesession.org 502 Bad Gateway.

#### Scenario: Import a Tune
- **GIVEN** thesession.org knows tune 27 as the reel "Cooley's"
- **WHEN** alice sends `POST /api/tunes/import` with id 27
- **THEN** the response status is 201 Created
- **AND** the Tune "Cooley's" is a reel with a Reference transcription

#### Scenario: Import twice
- **GIVEN** tune 27 has already been imported
- **WHEN** bob sends `POST /api/tunes/import` with id 27
- **THEN** the response status is 200 OK
- **AND** only one Tune "Cooley's" exists

#### Scenario: Unknown thesession.org id
- **GIVEN** thesession.org does not know tune 0
- **WHEN** alice sends `POST /api/tunes/import` with id 0
- **THEN** the response status is 404 Not Found

## ADDED Requirements

### Requirement: Bulk import of thesession.org Tunes
The system SHALL let an Administrator start a bulk import with `POST /api/tunes/import-dump`, which downloads
`tunes.csv` and `aliases.csv` from adactio/TheSession-data at run time and answers 202 Accepted. For each thesession.org
tune id not yet in the catalogue it SHALL create a Tune from the first setting of that id only (name, Dance type, usual
key, Reference transcription), with the dump's aliases and its composer as author, and no popularity. A Tune already in
the catalogue SHALL be left unchanged. A row with an empty name or an unknown Dance type SHALL be skipped. A request
from a non-Administrator SHALL answer 403 Forbidden, an unauthenticated one 401, and a request while an import is
running 409 Conflict.

#### Scenario: Import the dump
- **GIVEN** the dump has tune 27 "Cooley's" (reel) with two settings and the alias "Cooley's Reel", composer "Unknown"
- **WHEN** an Administrator sends `POST /api/tunes/import-dump` and the import finishes
- **THEN** one Tune "Cooley's" exists, a reel, with the Reference transcription of the first setting
- **AND** it has the alias "Cooley's Reel"

#### Scenario: Run again
- **GIVEN** an import has finished
- **WHEN** an Administrator starts it again
- **THEN** no Tune is created twice and no existing Tune changes

#### Scenario: Resume after an interruption
- **GIVEN** a run stopped after creating part of the Tunes
- **WHEN** an Administrator starts it again
- **THEN** the missing Tunes are created and the existing ones are untouched

#### Scenario: Not an Administrator
- **WHEN** alice, who is not an Administrator, sends `POST /api/tunes/import-dump`
- **THEN** the response status is 403 Forbidden

#### Scenario: Skipped rows
- **GIVEN** the dump has a row with Dance type "foxtrot"
- **WHEN** the import finishes
- **THEN** no Tune is created for it and the run counts it as skipped

### Requirement: Bulk import progress
The system SHALL let an Administrator read the last bulk import with `GET /api/tunes/import-dump`: its state (`idle`,
`running`, `done` or `failed`), the numbers of Tunes created and rows skipped, and, when failed, the reason. An
unreachable download SHALL end the run as `failed` and keep the Tunes already created.

#### Scenario: Read progress
- **GIVEN** an import is running
- **WHEN** an Administrator sends `GET /api/tunes/import-dump`
- **THEN** the state is `running` with the numbers so far

#### Scenario: Download fails
- **GIVEN** the dump cannot be downloaded
- **WHEN** an Administrator starts the import
- **THEN** the state becomes `failed` with a reason

### Requirement: thesession.org credit
The UI SHALL show the text "Data from thesession.org" on every page, as a link to thesession.org that is reachable by
keyboard and meets contrast in both themes.

#### Scenario: Credit visible
- **WHEN** any visitor opens any page
- **THEN** the link "Data from thesession.org" is shown
