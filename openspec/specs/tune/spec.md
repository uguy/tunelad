# tune Specification

## Purpose
Tunes and their Dance type: creating them by hand, importing them from thesession.org, browsing them, and viewing and playing their Reference transcription.

## Requirements

### Requirement: Dance types
The system SHALL give every Tune exactly one Dance type from a fixed list: jig, reel, hornpipe, slide, polka,
slip jig, waltz, barndance, strathspey, march, mazurka, three-two. `GET /api/dance-types` SHALL return the list.

#### Scenario: List dance types
- **WHEN** a client sends `GET /api/dance-types`
- **THEN** the response status is 200 OK
- **AND** the body contains "jig", "reel" and "hornpipe"

### Requirement: Create a Tune by hand
The system SHALL let an authenticated Account create a Tune with `POST /api/tunes` giving a name, a Dance type and
optionally aliases, an author, a usual key and a Reference transcription (ABC). The response SHALL be 201 Created with
the Tune. A missing name, or an unknown Dance type, SHALL answer 400 Bad Request, and an unauthenticated request 401.
A Tune created by hand has no thesession.org id.

#### Scenario: Create a Tune
- **GIVEN** alice is authenticated
- **WHEN** alice sends `POST /api/tunes` with name "The Kesh", Dance type "jig" and alias "Kesh Jig"
- **THEN** the response status is 201 Created
- **AND** the Tune "The Kesh" is a jig with the alias "Kesh Jig"

#### Scenario: Unknown Dance type
- **WHEN** alice sends `POST /api/tunes` with Dance type "foxtrot"
- **THEN** the response status is 400 Bad Request

### Requirement: Import a Tune from thesession.org
The system SHALL let an authenticated Account import a Tune with `POST /api/tunes/import` giving a thesession.org
tune id. The Tune SHALL take its name, aliases, Dance type, usual key and Reference transcription (a complete ABC tune) from
thesession.org, which publishes no author, so the author stays empty and keep the thesession.org id. Importing an id that was already imported SHALL answer 200 OK with the
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

### Requirement: Browse Tunes
The system SHALL list Tunes with `GET /api/tunes`, ordered by name, optionally filtered by `danceType` and by `q`
(case-insensitive match on name or alias), and return one Tune with `GET /api/tunes/{id}`, 404 if it does not exist.
Tunes are shared by all Accounts. Reading is open to anonymous visitors, like the library.

#### Scenario: Filter by Dance type
- **GIVEN** the Tunes "The Kesh" (jig) and "Cooley's" (reel)
- **WHEN** a client sends `GET /api/tunes?danceType=reel`
- **THEN** only "Cooley's" is returned

#### Scenario: Find by alias
- **GIVEN** "The Kesh" has the alias "Kesh Jig"
- **WHEN** a client sends `GET /api/tunes?q=kesh jig`
- **THEN** "The Kesh" is returned

### Requirement: View a Reference transcription
The Tune detail page SHALL let the user switch between the sheet music and the ABC text of the Reference transcription.
The sheet music SHALL be the initial view. The switch SHALL be a labelled control operable with the keyboard, and the sheet
SHALL have a text alternative. A Tune without a Reference transcription SHALL show a "no transcription" message and no switch.

#### Scenario: Switch to ABC text
- **GIVEN** alice opens the detail page of "Cooley's", which shows the sheet music
- **WHEN** she selects the ABC view
- **THEN** the ABC text is shown and the sheet music is hidden

#### Scenario: No transcription
- **GIVEN** "The Kesh" has no Reference transcription
- **WHEN** alice opens its detail page
- **THEN** a "no transcription" message is shown
- **AND** there is no view switch and no Play control

### Requirement: Play a Reference transcription
The Tune detail page SHALL offer a Play control that plays the Reference transcription as synthesized MIDI audio, a Stop
control and a tempo control. Starting playback SHALL pause the Track player. If audio cannot start, the page SHALL show an
error message.

#### Scenario: Play and stop
- **GIVEN** alice is on the detail page of "Cooley's"
- **WHEN** she activates Play
- **THEN** the control shows that the tune is playing
- **WHEN** she activates Stop
- **THEN** playback stops

#### Scenario: Pauses the Track player
- **GIVEN** a Track is playing
- **WHEN** alice activates Play on a Tune
- **THEN** the Track is paused

### Requirement: Tunes pages
The UI SHALL offer a Tunes page listing Tunes with a Dance type filter and a text filter, and a form to create a Tune or
import one by thesession.org id. The pages SHALL be keyboard accessible and use the theme tokens.

#### Scenario: Import from the UI
- **GIVEN** alice opens the Tunes page
- **WHEN** she enters thesession.org id 27 and submits
- **THEN** she lands on the detail page of "Cooley's"

### Requirement: Tune view shows each Performance's sheet
The Tune detail view SHALL draw a sheet for each Performance of the Tune that has a Transcription, labelled with its Track title and Artist and linking to the Track. When no Performance has a Transcription, it SHALL draw the Reference transcription instead; when it has neither, it SHALL show no sheet.

#### Scenario: Sheets from Performances
- **GIVEN** the Tune "The Kesh" has a Reference transcription and two Performances with a Transcription
- **WHEN** a visitor opens the Tune
- **THEN** one sheet is drawn per Performance, each labelled with its Track
- **AND** the Reference transcription is not drawn in place of them

#### Scenario: Fallback to the Reference transcription
- **GIVEN** the Tune has a Reference transcription and no Performance with a Transcription
- **WHEN** a visitor opens the Tune
- **THEN** the Reference transcription is drawn

#### Scenario: No transcription at all
- **GIVEN** the Tune has no Reference transcription and no Transcription on any Performance
- **WHEN** a visitor opens the Tune
- **THEN** no sheet is drawn

### Requirement: Edit a Performance's Transcription in the UI
The Track edit form SHALL offer a monospaced ABC field in each Performance's fields, saved with the Track, with its limit announced and a validation message when exceeded.

#### Scenario: Edit and save
- **GIVEN** alice edits her Track
- **WHEN** she types ABC into a Performance's Transcription field and saves
- **THEN** the Track is sent with that `abc` for the Performance
