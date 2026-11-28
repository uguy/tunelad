## ADDED Requirements

### Requirement: Tune view shows who recorded it
The Tune detail view SHALL show a "Recorded by" list of the distinct Artists of the Tune's Performances, each linking to nothing more than its name and kind (Musician, Band, or no mention when unclassified). A Band SHALL list its members beneath it. The list SHALL be absent when no Performance has an Artist. An Account SHALL be able to classify an unclassified Artist and edit a Band's members from there; a visitor without an Account SHALL see the list read-only.

#### Scenario: Musicians and Bands
- **GIVEN** "The Kesh" has Performances in a Track by the Band "Altan" (member "Mairéad") and in a Track by the Musician "Paul Brock"
- **WHEN** a visitor opens the Tune
- **THEN** "Recorded by" shows "Altan" as a Band with "Mairéad" and "Paul Brock" as a Musician

#### Scenario: Same Artist once
- **GIVEN** two Tracks by "Altan" both with a Performance of the Tune
- **WHEN** a visitor opens the Tune
- **THEN** "Altan" appears once

#### Scenario: No Artist
- **GIVEN** no Performance of the Tune has an Artist
- **WHEN** a visitor opens the Tune
- **THEN** there is no "Recorded by" list

#### Scenario: Classify from the Tune
- **GIVEN** an Account is signed in and "Altan" is unclassified
- **WHEN** it classifies "Altan" as a Band and adds the member "Mairéad"
- **THEN** the Tune view shows "Altan" as a Band with "Mairéad"

#### Scenario: Visitor cannot edit
- **WHEN** a visitor opens the Tune
- **THEN** no classify or member controls are offered
