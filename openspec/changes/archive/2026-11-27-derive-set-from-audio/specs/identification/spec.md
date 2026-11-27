# Spec Delta

## MODIFIED Requirements

### Requirement: Identification runs after upload
After a Track is saved, the system SHALL start Identification of its audio in the background, without delaying the upload response. For a Track with a Set it looks for the Tunes of that Set and, until it finishes, every Performance of the Track SHALL have the state `pending`. For a Track without a Set it listens for any Tune and may give the Track a Set.

#### Scenario: Upload returns before analysis ends
- **GIVEN** a Set of "Cooley's" then "The Kesh" exists and the sources are slow
- **WHEN** an authenticated client uploads a track for it
- **THEN** the response status is 201 Created
- **AND** both Performances of the track have the state `pending`

#### Scenario: Upload without a Set returns before analysis ends
- **GIVEN** the analysis service is slow
- **WHEN** an authenticated client uploads a file with no `setId`
- **THEN** the response status is 201 Created
- **AND** the track has no Set until the analysis ends

### Requirement: Re-run Identification
The system SHALL let the Owner of a Track or an Administrator re-run Identification with `POST /api/tracks/{id}/identification`, responding 202 Accepted. For a Track without a Set the re-run is the open-ended analysis. Other Accounts get 403 Forbidden, an unknown Track 404 Not Found.

#### Scenario: Owner re-runs
- **WHEN** the Owner requests a re-run
- **THEN** the response status is 202 Accepted and Identification runs again

#### Scenario: Owner re-runs a track without a Set
- **GIVEN** a Track without a Set whose first analysis recognised nothing
- **WHEN** the Owner requests a re-run and the analysis now recognises "Cooley's"
- **THEN** the response status is 202 Accepted and the Track gets the Set of "Cooley's"

#### Scenario: Not the Owner
- **WHEN** an Account that is neither Owner nor Administrator requests a re-run
- **THEN** the response status is 403 Forbidden

## REMOVED Requirements

### Requirement: A track without a Set is not identified

## ADDED Requirements

### Requirement: The Set of a Track without one is derived from its audio
For a Track without a Set, Identification SHALL ask the analysis service for every Tune it recognises, keep those it is confident about, and give the Track the Set of those Tunes in the order they were first heard, with one Performance per Tune in that order whose start is where the Tune was heard and whose state is `identified`.

#### Scenario: Two tunes are recognised
- **GIVEN** a Track without a Set whose audio plays "Cooley's" from 0 s and "The Kesh" from 95 s, and the Tunes are in the catalogue
- **WHEN** Identification runs
- **THEN** the Track has the Set of "Cooley's" then "The Kesh"
- **AND** its Performances are "Cooley's" starting at 0 s then "The Kesh" starting at 95 s, both `identified`
- **AND** a client connected to `GET /api/live` receives `track.indexed` with the Track's id

### Requirement: A derived Set is a Set like any other
A Set found or created by Identification SHALL follow the rules of every Set: one Set per distinct ordered list of Tunes, and a Set of one Tune is valid. The Track SHALL be announced as updated, so searches and connected browsers show the Set and its Performances without a reload.

#### Scenario: The Set already exists
- **GIVEN** the Set of "Cooley's" then "The Kesh" exists and a Track without a Set plays them in that order
- **WHEN** Identification runs
- **THEN** the Track has that existing Set and no second Set exists

#### Scenario: Another order is another Set
- **GIVEN** the Set of "Cooley's" then "The Kesh" exists and a Track without a Set plays "The Kesh" first
- **WHEN** Identification runs
- **THEN** the Track has a new Set of "The Kesh" then "Cooley's"

#### Scenario: One tune
- **GIVEN** a Track without a Set whose audio plays only "Cooley's"
- **WHEN** Identification runs
- **THEN** the Track has the Set of "Cooley's" alone, with one `identified` Performance

### Requirement: Which recognised Tunes are kept
Identification SHALL ignore a detection whose confidence is below the configured minimum (`tunelad.identification.min-confidence`, default 0.5), merge consecutive detections of the same Tune into one, keep a Tune heard again after another Tune as a further entry, and keep at most the first twenty entries, the limit of a Set.

#### Scenario: Weak detections are ignored
- **GIVEN** the analysis hears "Cooley's" with confidence 0.8 and "The Kesh" with confidence 0.2
- **WHEN** Identification runs on a Track without a Set
- **THEN** the Track's Set is the Set of "Cooley's" alone

#### Scenario: A split detection is one entry
- **GIVEN** the analysis hears "Cooley's" from 0 s, again from 20 s with no other Tune between, then "The Kesh" from 95 s
- **WHEN** Identification runs on a Track without a Set
- **THEN** the Track's Set is "Cooley's" then "The Kesh"

#### Scenario: A repeated tune
- **GIVEN** the analysis hears "Cooley's", then "The Kesh", then "Cooley's" again
- **WHEN** Identification runs on a Track without a Set
- **THEN** the Track's Set is "Cooley's", "The Kesh", "Cooley's", with three Performances

#### Scenario: More than twenty entries
- **GIVEN** the analysis hears twenty-five different Tunes
- **WHEN** Identification runs on a Track without a Set
- **THEN** the Track's Set has the first twenty, in the order heard

### Requirement: Nothing recognised leaves the Track as it is
When no detection is kept, or none of the kept Tunes can be found or imported, Identification SHALL leave the Track without a Set and unchanged, and an Account SHALL be able to run Identification again.

#### Scenario: Nothing heard
- **GIVEN** the analysis recognises no Tune
- **WHEN** Identification runs on a Track without a Set
- **THEN** the Track still has no Set, no Performances and its other fields as before

#### Scenario: Recognised but unusable
- **GIVEN** the analysis hears a Tune that thesession.org does not have
- **WHEN** Identification runs on a Track without a Set
- **THEN** the Track still has no Set

#### Scenario: One tune cannot be imported
- **GIVEN** the analysis hears "Cooley's" then a Tune thesession.org cannot be reached for, then "The Kesh"
- **WHEN** Identification runs on a Track without a Set
- **THEN** the Track's Set is "Cooley's" then "The Kesh"

### Requirement: A Set an Account chose is never replaced
Open-ended Identification SHALL give a Set only to a Track that still has none when it finishes. A Track that has a Set, from upload or given while the analysis ran, SHALL keep it and its Performances unchanged by the open-ended result, and is analysed only against the Tunes of its Set.

#### Scenario: A Track with a Set is analysed as before
- **GIVEN** a Track with the Set of "Cooley's" then "The Kesh"
- **WHEN** Identification runs
- **THEN** the analysis service is asked only for those two Tunes and the Track's Set is unchanged

#### Scenario: The Set arrives while the analysis runs
- **GIVEN** a Track without a Set is being analysed
- **WHEN** the Track is given a Set before the analysis ends
- **THEN** the result of the open-ended analysis is discarded and the Track keeps its Set and Performances

### Requirement: The analysis service can fail without a Set
If the analysis service is unreachable, times out or answers with an error for a Track without a Set, Identification SHALL end without failing the Track and leave it as it was, and allow a re-run.

#### Scenario: Service down
- **GIVEN** the analysis service is down
- **WHEN** a Track without a Set is uploaded
- **THEN** the upload succeeds, the Track has no Set, and a later re-run can still give it one
