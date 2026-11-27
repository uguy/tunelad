## ADDED Requirements

### Requirement: Identification runs after upload
After a Track is saved with a Set, the system SHALL start Identification of its audio in the background, without delaying the upload response. Until it finishes, every Performance of the Track SHALL have the state `pending`.

#### Scenario: Upload returns before analysis ends
- **GIVEN** a Set of "Cooley's" then "The Kesh" exists and the sources are slow
- **WHEN** an authenticated client uploads a track for it
- **THEN** the response status is 201 Created
- **AND** both Performances of the track have the state `pending`

### Requirement: Proposals fill Performances
When Identification finishes, the system SHALL, for each Performance whose Tune the analysis detected in the Track's audio, propose the detection's start as the Performance's start offset and set its state to `identified`. Only Tunes of the Track's Set SHALL be accepted: a detected Tune that is not in the Set is ignored. A Performance whose Tune was not detected SHALL keep its values and its state `pending`.

#### Scenario: Proposal accepted
- **GIVEN** a track of the Set "Cooley's" then "The Kesh" whose Performances are `pending`, and the analysis detects "The Kesh" starting at 95.5 s
- **WHEN** Identification finishes
- **THEN** the second Performance has start offset 95.5 and the state `identified`
- **AND** the first Performance is unchanged and `pending`

#### Scenario: Tune outside the Set
- **GIVEN** the analysis detects a Tune that is not in the Track's Set
- **WHEN** Identification finishes
- **THEN** no Performance is changed

#### Scenario: Nothing detected
- **GIVEN** a track whose Performances are `pending` and the analysis detects none of its Tunes
- **WHEN** Identification finishes
- **THEN** the Performances keep their values and stay `pending`

### Requirement: Manual corrections are never overwritten
A Performance in the state `corrected` SHALL keep its values through any Identification, including a re-run. A proposal SHALL replace only values of Performances that are `pending` or `identified`.

#### Scenario: Re-run keeps a correction
- **GIVEN** an Account edited the start offset of a Performance to 90, making it `corrected`
- **WHEN** Identification is re-run and detects that Tune starting at 95.5 s
- **THEN** the start offset is still 90 and the state is still `corrected`

### Requirement: Re-run Identification
The system SHALL let the Owner of a Track or an Administrator re-run Identification with `POST /api/tracks/{id}/identification`, responding 202 Accepted. Other Accounts get 403 Forbidden, an unknown Track 404 Not Found.

#### Scenario: Owner re-runs
- **WHEN** the Owner requests a re-run
- **THEN** the response status is 202 Accepted and Identification runs again

#### Scenario: Not the Owner
- **WHEN** an Account that is neither Owner nor Administrator requests a re-run
- **THEN** the response status is 403 Forbidden

### Requirement: The analysis service can fail
If the analysis service is unreachable, times out or answers with an error, Identification SHALL end without failing the Track, leave the Performances unchanged and allow a re-run.

#### Scenario: Service down
- **GIVEN** the analysis service is unreachable
- **WHEN** Identification runs
- **THEN** the Track and its Performances are unchanged and a re-run is possible
