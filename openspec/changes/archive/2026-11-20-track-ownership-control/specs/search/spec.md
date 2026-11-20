# Spec Delta

## ADDED Requirements

### Requirement: Edits and deletions reach search shortly after
After a track edit or deletion completes successfully, the system SHALL reflect it in search results within 5 seconds:
an edited track SHALL be matched on its new metadata and returned with it, and a deleted track SHALL no longer be
returned. Searches made earlier than that are not required to reflect the change.

#### Scenario: Edited track found by its new title
- **GIVEN** a searchable track titled "Knees Up"
- **WHEN** its owner edits the title to "Hornpipe Set" and the edit succeeds
- **THEN** within 5 seconds, a search for `hornpipe` returns that track with the title "Hornpipe Set"
- **AND** a search for `knees` no longer returns it

#### Scenario: Edited track has the track list fields
- **GIVEN** a searchable track whose metadata has just been edited
- **WHEN** a search returns it after the edit has reached search
- **THEN** its `id`, `title`, `description`, `artist`, `album`, `format` and `owner` equal those of the same track in `GET /api/tracks`

#### Scenario: Deleted track disappears from search
- **GIVEN** searchable tracks titled "Knees Up" and "Mossy Banks"
- **WHEN** the owner of "Knees Up" deletes it and the deletion succeeds
- **THEN** within 5 seconds, a blank search returns "Mossy Banks" and not "Knees Up"
