# Spec Delta

## ADDED Requirements

### Requirement: The tracks list shows other users' changes without a reload
While the tracks page is open, the UI SHALL show uploads, edits and deletions made by other users once they are
searchable, without a reload and without any action by the visitor, whether the visitor is logged in or not.

#### Scenario: Another user uploads a track
- **GIVEN** an anonymous visitor has the tracks page open
- **WHEN** bob uploads a track titled "Mossy Banks"
- **THEN** "Mossy Banks" appears in the visitor's list within 5 seconds of becoming searchable

#### Scenario: Another user edits a track
- **GIVEN** alice has the tracks page open showing "Mossy Banks"
- **WHEN** bob renames it to "Mossy Banks (live)"
- **THEN** alice's list shows the new title

#### Scenario: Another user deletes a track
- **GIVEN** alice has the tracks page open showing "Mossy Banks"
- **WHEN** bob deletes it
- **THEN** "Mossy Banks" disappears from alice's list

### Requirement: The user's own changes are not shown twice
A track the visitor uploaded, edited or deleted themselves SHALL appear in the list exactly once, in its final state,
whether the list was updated by the visitor's own action or by the live update that follows it.

#### Scenario: Own upload
- **GIVEN** alice is on the tracks page
- **WHEN** she uploads "Knees Up" and the live update for it arrives
- **THEN** the list contains "Knees Up" exactly once

#### Scenario: Own delete
- **GIVEN** alice is on the tracks page showing her track "Knees Up"
- **WHEN** she deletes it and the live update for it arrives
- **THEN** the list does not contain "Knees Up" and shows no error
