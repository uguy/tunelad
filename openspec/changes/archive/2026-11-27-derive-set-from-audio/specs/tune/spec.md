# Spec Delta

## ADDED Requirements

### Requirement: Identification imports the Tunes it recognises
When open-ended Identification recognises a Tune that the catalogue does not have, the system SHALL import it from thesession.org by its id, as `POST /api/tunes/import` does, owned by the Owner of the Track being identified; a Tune already in the catalogue SHALL be used as it is, and one that cannot be imported SHALL be left out of the Set.

#### Scenario: An unknown Tune is imported
- **GIVEN** alice uploaded a track without a Set, and the catalogue lacks "Cooley's", which the analysis hears
- **WHEN** Identification runs
- **THEN** "Cooley's" is in the Tunes list with its thesession.org id, and the track's Set includes it

#### Scenario: A known Tune is not imported again
- **GIVEN** "Cooley's" was imported earlier and the analysis hears it
- **WHEN** Identification runs on a track without a Set
- **THEN** no second "Cooley's" exists and the track's Set uses the existing Tune
