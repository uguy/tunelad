# Spec Delta

## ADDED Requirements

### Requirement: Edit a track's metadata
The system SHALL replace the `title`, `description`, `artist` and `album` of the track with the given id with those
of the JSON body on `PUT /api/tracks/{id}`, and respond 200 OK with the updated track. An omitted or null `artist` or
`album` SHALL clear it. The track's `id`, `format`, `owner` and audio SHALL stay unchanged.

#### Scenario: Owner edits all metadata
- **GIVEN** alice has uploaded a track titled "Knees Up" with artist "Breabach"
- **WHEN** alice puts the title "Knees Up (live)", description "Recorded at Celtic Connections", artist "Breabach" and album "Live" to `/api/tracks/{id}`
- **THEN** the response status is 200 OK and the body is the track with that title, description, artist and album
- **AND** `GET /api/tracks/{id}` returns the same metadata, with the same `id`, `format` and `owner` as before

#### Scenario: Optional metadata omitted
- **GIVEN** alice has uploaded a track with an artist and an album
- **WHEN** alice puts only a title and a description to `/api/tracks/{id}`
- **THEN** the response status is 200 OK
- **AND** the track has no artist and no album

#### Scenario: Audio unchanged after an edit
- **GIVEN** alice has uploaded a track with an mp3 file
- **WHEN** alice edits its metadata
- **THEN** `GET /api/tracks/{id}/play` still returns a body byte-for-byte identical to the uploaded file

#### Scenario: Required field missing or blank
- **GIVEN** alice has uploaded a track titled "Knees Up"
- **WHEN** alice puts a body with no `title`, a blank `title`, no `description` or a blank `description` to `/api/tracks/{id}`
- **THEN** the response status is 400 Bad Request
- **AND** the track is still titled "Knees Up"

#### Scenario: Edit an unknown track
- **WHEN** an authenticated client puts valid metadata to `/api/tracks/{id}` with an id no track has
- **THEN** the response status is 404 Not Found

### Requirement: Delete a track
The system SHALL remove the track with the given id, together with its stored audio, on `DELETE /api/tracks/{id}`,
and respond 204 No Content. Afterwards, the track SHALL no longer be listed, fetched or played.

#### Scenario: Owner deletes a track
- **GIVEN** alice has uploaded the tracks "Knees Up" and "Mossy Banks"
- **WHEN** alice deletes "Knees Up"
- **THEN** the response status is 204 No Content
- **AND** `GET /api/tracks` contains "Mossy Banks" and not "Knees Up"
- **AND** `GET /api/tracks/{id}` for "Knees Up" responds 404 Not Found

#### Scenario: Deleted track can no longer be played
- **GIVEN** alice has uploaded a track and then deleted it
- **WHEN** a client requests `GET /api/tracks/{id}/play` for it
- **THEN** the response status is 404 Not Found

#### Scenario: Delete an unknown track
- **WHEN** an authenticated client deletes `/api/tracks/{id}` with an id no track has
- **THEN** the response status is 404 Not Found

### Requirement: Only the owner or an administrator changes a track
The system SHALL let only the track's owner or an administrator (as defined by the `account` capability) edit or
delete a track. It SHALL answer any other authenticated user with 403 Forbidden, and leave the track unchanged. An
unknown id SHALL be answered with 404 Not Found whoever asks.

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

### Requirement: Changing a track requires authentication
The system SHALL reject an edit or a deletion that carries neither a valid session nor a valid bearer token with 401
Unauthorized, without redirecting, and SHALL leave the track unchanged.

#### Scenario: Anonymous edit
- **GIVEN** alice has uploaded a track titled "Knees Up"
- **WHEN** a client without a session or token puts new metadata to `/api/tracks/{id}` for it
- **THEN** the response status is 401 Unauthorized and the response is not a redirect
- **AND** the track is still titled "Knees Up"

#### Scenario: Anonymous delete
- **GIVEN** alice has uploaded a track
- **WHEN** a client without a session or token deletes it
- **THEN** the response status is 401 Unauthorized and the response is not a redirect
- **AND** the track is still listed

### Requirement: Edit and delete own tracks from the UI
The UI track list SHALL show "Edit" and "Delete" controls on the tracks the logged-in user may change: their own
tracks, or every track for an administrator. It SHALL show none to anonymous visitors.

#### Scenario: Controls on own tracks only
- **GIVEN** the track list contains "Knees Up" owned by alice and "Mossy Banks" owned by bob
- **AND** alice, who is not an administrator, is logged in
- **WHEN** alice opens the tracks page
- **THEN** "Knees Up" has "Edit" and "Delete" controls
- **AND** "Mossy Banks" has neither

#### Scenario: Administrator sees controls on every track
- **GIVEN** the track list contains tracks owned by alice and by bob
- **AND** an administrator is logged in
- **WHEN** the administrator opens the tracks page
- **THEN** every track has "Edit" and "Delete" controls

#### Scenario: No controls for anonymous visitors
- **GIVEN** nobody is logged in
- **WHEN** a visitor opens the tracks page
- **THEN** no track has an "Edit" or a "Delete" control

### Requirement: Edit a track from the UI
Activating "Edit" on a track SHALL show a form with its title, description, artist and album. Saving SHALL send the
edit with the CSRF token and show the updated track in the list as soon as the server accepts it. Cancelling SHALL
leave the track unchanged. A rejected edit SHALL show an error and keep the track as it was.

#### Scenario: Save an edit
- **GIVEN** alice is logged in and the list shows her track "Knees Up"
- **WHEN** alice activates "Edit" on it, changes the title to "Knees Up (live)" and saves
- **THEN** the browser sends `PUT /api/tracks/{id}` with the new metadata and the `X-XSRF-TOKEN` header
- **AND** the list shows "Knees Up (live)" in place of "Knees Up"

#### Scenario: Cancel an edit
- **GIVEN** alice is editing her track "Knees Up"
- **WHEN** alice changes the title and cancels
- **THEN** no request is sent and the list still shows "Knees Up"

#### Scenario: Edit rejected
- **GIVEN** alice is editing her track "Knees Up"
- **WHEN** alice saves and the server answers 403 Forbidden
- **THEN** an error message is shown
- **AND** the list still shows "Knees Up"

### Requirement: Delete a track from the UI
Activating "Delete" on a track SHALL ask for confirmation. Once confirmed, the UI SHALL send the deletion with the
CSRF token and remove the track from the list as soon as the server accepts it. Declining SHALL send nothing. A
rejected deletion SHALL show an error and keep the track in the list.

#### Scenario: Confirm a deletion
- **GIVEN** alice is logged in and the list shows her track "Knees Up"
- **WHEN** alice activates "Delete" on it and confirms
- **THEN** the browser sends `DELETE /api/tracks/{id}` with the `X-XSRF-TOKEN` header
- **AND** "Knees Up" disappears from the list

#### Scenario: Decline a deletion
- **GIVEN** alice is logged in and the list shows her track "Knees Up"
- **WHEN** alice activates "Delete" on it and declines the confirmation
- **THEN** no request is sent and the list still shows "Knees Up"

#### Scenario: Deletion rejected
- **GIVEN** alice is logged in and the list shows her track "Knees Up"
- **WHEN** alice confirms its deletion and the server answers 403 Forbidden
- **THEN** an error message is shown
- **AND** the list still shows "Knees Up"
