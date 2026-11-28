## ADDED Requirements

### Requirement: Tune view shows the Albums it appears on
The Tune view SHALL list the Albums that have a Track with a resolved Performance of the Tune, including Catalogue Tracks,
each linking to its Album view.

#### Scenario: Albums of a Tune
- **GIVEN** the Album "At the End of the Day" has a Catalogue Track with a Performance of "Cooley's"
- **WHEN** the Tune view of "Cooley's" is opened
- **THEN** it lists "At the End of the Day" by Dervish
