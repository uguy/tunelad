## ADDED Requirements

### Requirement: Performance shows its Identification state
Every Performance in a track's JSON SHALL carry `identification`, one of `pending`, `identified` or `corrected`. Performances created at upload start as `pending`; Performances stored before Identification existed are `corrected`.

#### Scenario: New track
- **WHEN** an authenticated client uploads a track for a Set
- **THEN** each Performance in the response has `identification` `pending`

### Requirement: Editing a Performance marks it corrected
Changing a Performance's start offset, key, notes or Transcription through the track edit endpoint SHALL set its state to `corrected`. Saving it with unchanged values SHALL leave the state as it was.

#### Scenario: Key edited
- **GIVEN** a Performance in the state `identified` with key "G"
- **WHEN** its Owner updates the key to "Em"
- **THEN** the Performance has key "Em" and the state `corrected`

#### Scenario: Saved unchanged
- **GIVEN** a Performance in the state `identified`
- **WHEN** its Owner saves the track with the same values
- **THEN** the state is still `identified`
