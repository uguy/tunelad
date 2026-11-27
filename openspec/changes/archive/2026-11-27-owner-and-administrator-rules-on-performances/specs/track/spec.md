## MODIFIED Requirements

### Requirement: Only the owner or an administrator changes a track
The system SHALL let only the track's owner or an administrator (as defined by the `account` capability) edit or
delete a track, including its Performances. It SHALL answer any other authenticated user with 403 Forbidden, and leave
the track and its Performances unchanged. An unknown id SHALL be answered with 404 Not Found whoever asks.

#### Scenario: Another user edits a track
- **GIVEN** alice has uploaded a track titled "Knees Up"
- **WHEN** bob, who is not an administrator, puts new metadata to `/api/tracks/{id}` for it
- **THEN** the response status is 403 Forbidden
- **AND** the track is still titled "Knees Up"

#### Scenario: Another user deletes a track
- **GIVEN** alice has uploaded a track
- **WHEN** bob, who is not an administrator, deletes it
- **THEN** the response status is 403 Forbidden
- **AND** the track is still listed and can still be played

#### Scenario: Administrator edits someone else's track
- **GIVEN** alice has uploaded a track titled "Knees Up"
- **WHEN** an administrator puts the title "Knees Up (edited)" and a description to `/api/tracks/{id}`
- **THEN** the response status is 200 OK
- **AND** the track is titled "Knees Up (edited)" and its `owner` is still alice

#### Scenario: Administrator deletes someone else's track
- **GIVEN** alice has uploaded a track
- **WHEN** an administrator deletes it
- **THEN** the response status is 204 No Content
- **AND** the track is no longer listed

#### Scenario: Owner edits the performances of their track
- **GIVEN** alice has uploaded a track of a set of two tunes
- **WHEN** alice puts a key and notes for each performance to `/api/tracks/{id}`
- **THEN** the response status is 200 OK and each performance has that key and those notes

#### Scenario: Another user edits the performances of a track
- **GIVEN** alice has uploaded a track of a set of two tunes
- **WHEN** bob, who is not an administrator, puts a key for the first performance to `/api/tracks/{id}`
- **THEN** the response status is 403 Forbidden
- **AND** the first performance still has no key

#### Scenario: Administrator edits the performances of someone else's track
- **GIVEN** alice has uploaded a track of a set of two tunes
- **WHEN** an administrator puts a key for the first performance to `/api/tracks/{id}`
- **THEN** the response status is 200 OK and the first performance has that key
- **AND** the track's `owner` is still alice
