## ADDED Requirements

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
