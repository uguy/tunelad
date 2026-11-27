## ADDED Requirements

### Requirement: Identification runs after upload
After a Track is saved with a Set, the system SHALL start Identification of its audio in the background, without delaying the upload response. Until it finishes, every Performance of the Track SHALL have the state `pending`.

#### Scenario: Upload returns before analysis ends
- **GIVEN** a Set of "Cooley's" then "The Kesh" exists and the sources are slow
- **WHEN** an authenticated client uploads a track for it
- **THEN** the response status is 201 Created
- **AND** both Performances of the track have the state `pending`

### Requirement: Proposals fill Performances
When Identification finishes, the system SHALL propose for each Performance a start offset, a key and a Transcription, and set its state to `identified`. A source that has no answer for a Performance SHALL leave that Performance's values unchanged and its state `pending` becomes `identified` only if something was proposed.

#### Scenario: Proposal accepted
- **GIVEN** a track whose Performances are `pending` and the sources propose start offset 95.5, key "G" and an ABC Transcription for the second
- **WHEN** Identification finishes
- **THEN** the second Performance has those values and the state `identified`

#### Scenario: No proposal
- **GIVEN** a track whose Performances are `pending` and no source proposes anything
- **WHEN** Identification finishes
- **THEN** the Performances keep their values and none is `identified`

### Requirement: Manual corrections are never overwritten
A Performance in the state `corrected` SHALL keep its values through any Identification, including a re-run. A proposal SHALL replace only values of Performances that are `pending` or `identified`.

#### Scenario: Re-run keeps a correction
- **GIVEN** an Account edited the key of a Performance to "Dm", making it `corrected`
- **WHEN** Identification is re-run and proposes key "Em"
- **THEN** the key is still "Dm" and the state is still `corrected`

### Requirement: Re-run Identification
The system SHALL let the Owner of a Track or an Administrator re-run Identification with `POST /api/tracks/{id}/identification`, responding 202 Accepted. Other Accounts get 403 Forbidden, an unknown Track 404 Not Found.

#### Scenario: Owner re-runs
- **WHEN** the Owner requests a re-run
- **THEN** the response status is 202 Accepted and Identification runs again

#### Scenario: Not the Owner
- **WHEN** an Account that is neither Owner nor Administrator requests a re-run
- **THEN** the response status is 403 Forbidden

### Requirement: Sources can fail
If a source is unreachable or answers with an error, Identification SHALL end without failing the Track, leave the Performances unchanged and allow a re-run.

#### Scenario: Source down
- **GIVEN** FolkFriend is unreachable
- **WHEN** Identification runs
- **THEN** the Track and its Performances are unchanged and a re-run is possible
