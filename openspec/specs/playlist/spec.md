# playlist Specification

## Purpose
TBD - created by archiving change redesign-ui-favourites-playlists. Update Purpose after archive.

## Requirements

### Requirement: Create and list my playlists
The system SHALL let an authenticated user create a playlist with `POST /api/playlists`, giving a `name` (required,
1 to 80 characters, not blank) and an optional `description`, and SHALL answer 201 Created with the playlist and its
`Location`. `GET /api/playlists` SHALL return the authenticated user's playlists, most recently changed first, each
with its `id`, `name`, `description`, `trackCount` and `updatedAt`. Playlists are private: a user never sees or
reaches another user's playlist, which answers 404 Not Found as if it did not exist. An unauthenticated request
SHALL answer 401 Unauthorized.

#### Scenario: Create a playlist
- **GIVEN** alice is authenticated
- **WHEN** alice posts `{"name": "Session tunes"}` to `/api/playlists`
- **THEN** the response status is 201 Created and the body has an `id`, the name "Session tunes" and `trackCount` 0

#### Scenario: Blank name
- **WHEN** alice posts `{"name": "  "}` to `/api/playlists`
- **THEN** the response status is 400 Bad Request and no playlist is created

#### Scenario: List my playlists
- **GIVEN** alice has the playlists "Session tunes" and "Slow airs", and bob has one
- **WHEN** alice requests `GET /api/playlists`
- **THEN** the response contains alice's two playlists and not bob's

#### Scenario: Someone else's playlist
- **GIVEN** bob owns a playlist
- **WHEN** alice requests `GET /api/playlists/{id}` with its id
- **THEN** the response status is 404 Not Found

#### Scenario: Anonymous request
- **WHEN** a client without a session or token requests `GET /api/playlists`
- **THEN** the response status is 401 Unauthorized

### Requirement: Read, rename and delete a playlist
The system SHALL return a playlist with its tracks, in playlist order, on `GET /api/playlists/{id}`, SHALL replace
its `name` and `description` on `PUT /api/playlists/{id}`, and SHALL delete it, without deleting its tracks, on
`DELETE /api/playlists/{id}` (204 No Content). A playlist that does not exist, or belongs to someone else, answers
404 Not Found.

#### Scenario: Read with tracks in order
- **GIVEN** alice's playlist holds "Mossy Banks" then "Knees Up"
- **WHEN** alice requests `GET /api/playlists/{id}`
- **THEN** the body has the playlist's details and its `tracks` array lists "Mossy Banks" before "Knees Up"

#### Scenario: Rename
- **WHEN** alice sends `PUT /api/playlists/{id}` with the name "Reels"
- **THEN** the response status is 200 OK and the playlist is named "Reels"

#### Scenario: Delete keeps the tracks
- **GIVEN** alice's playlist holds "Knees Up"
- **WHEN** alice sends `DELETE /api/playlists/{id}`
- **THEN** the response status is 204 No Content
- **AND** the playlist no longer exists and the track "Knees Up" still does

### Requirement: Add, remove and reorder tracks in a playlist
The system SHALL add a track to the end of a playlist with `PUT /api/playlists/{id}/tracks/{trackId}`, remove it with
`DELETE /api/playlists/{id}/tracks/{trackId}`, and set the whole order with `PUT /api/playlists/{id}/tracks` given
the list of track ids. A track appears at most once in a playlist: adding it again changes nothing. Adding a track
that does not exist answers 404 Not Found. Reordering with a list that is not exactly the playlist's current track
ids answers 400 Bad Request and changes nothing.

#### Scenario: Add a track
- **GIVEN** alice's playlist is empty and "Knees Up" exists
- **WHEN** alice sends `PUT /api/playlists/{id}/tracks/{trackId}`
- **THEN** the response status is 204 No Content
- **AND** the playlist holds "Knees Up"

#### Scenario: Adding twice
- **GIVEN** the playlist already holds "Knees Up"
- **WHEN** alice adds "Knees Up" again
- **THEN** the playlist still holds it once

#### Scenario: Unknown track
- **WHEN** alice adds a track id that does not exist
- **THEN** the response status is 404 Not Found

#### Scenario: Remove a track
- **GIVEN** the playlist holds "Knees Up" and "Mossy Banks"
- **WHEN** alice sends `DELETE /api/playlists/{id}/tracks/{trackId}` for "Knees Up"
- **THEN** the playlist holds only "Mossy Banks"

#### Scenario: Reorder
- **GIVEN** the playlist holds "Knees Up" then "Mossy Banks"
- **WHEN** alice sends `PUT /api/playlists/{id}/tracks` with the ids in the order "Mossy Banks", "Knees Up"
- **THEN** reading the playlist lists "Mossy Banks" first

#### Scenario: Reorder with a wrong list
- **GIVEN** the playlist holds "Knees Up" and "Mossy Banks"
- **WHEN** alice sends a new order that lists only "Knees Up"
- **THEN** the response status is 400 Bad Request and the order is unchanged

### Requirement: A deleted track leaves every playlist
The system SHALL remove a track from every playlist that holds it when the track is deleted, keeping the order of
the other tracks.

#### Scenario: Track deleted
- **GIVEN** a playlist holds "Knees Up", "Mossy Banks" and "Slow Air" in that order
- **WHEN** the track "Mossy Banks" is deleted
- **THEN** the playlist holds "Knees Up" then "Slow Air"

### Requirement: Playlists pages
The UI SHALL list the user's playlists on `/tracks/playlists`, with a control to create one, and SHALL show a
playlist on `/tracks/playlists/{id}` with its name, description, tracks in order, controls to rename or delete the
playlist, to remove a track, and to move a track by drag and drop or with "Move up" and "Move down" buttons. Both pages SHALL show an
empty state when there is nothing to list. For an anonymous visitor they SHALL offer a link to log in. An unknown
playlist SHALL show a "not found" message.

#### Scenario: Playlists listed
- **GIVEN** `GET /api/playlists` returns "Session tunes" with 3 tracks
- **WHEN** the user opens `/tracks/playlists`
- **THEN** "Session tunes" is listed with its track count and links to its page

#### Scenario: Create from the list
- **GIVEN** the user is logged in
- **WHEN** the user creates a playlist named "Reels" from `/tracks/playlists`
- **THEN** the browser posts it to `/api/playlists` and "Reels" is listed

#### Scenario: Empty playlist
- **GIVEN** the user's playlist holds no track
- **WHEN** the user opens its page
- **THEN** the page says the playlist is empty and suggests adding tracks from the Tracks page

#### Scenario: Move a track
- **GIVEN** the playlist page lists "Knees Up" above "Mossy Banks"
- **WHEN** the user activates "Move down" on "Knees Up"
- **THEN** the browser sends the new order to `PUT /api/playlists/{id}/tracks`
- **AND** "Mossy Banks" is listed above "Knees Up"

#### Scenario: Unknown playlist
- **GIVEN** `GET /api/playlists/{id}` answers 404 Not Found
- **WHEN** the user opens that page
- **THEN** the page says the playlist was not found

#### Scenario: Anonymous visitor
- **GIVEN** `GET /api/me` responds 401 Unauthorized
- **WHEN** a visitor opens `/tracks/playlists`
- **THEN** the page offers a "Log in" link instead of a list

### Requirement: Reorder a playlist by drag and drop
The UI SHALL let the user reorder the tracks of a playlist by dragging a track row to another position, showing
where it will land while it is dragged, and SHALL save the new order with the same call as the buttons. The "Move up"
and "Move down" buttons SHALL remain on every row, as the alternative for keyboard and touch users, and SHALL reach
the same result. A failed save SHALL restore the previous order and tell the user.

#### Scenario: Drag a track down
- **GIVEN** the playlist page lists "Knees Up", "Mossy Banks" and "Slow Air"
- **WHEN** the user drags "Knees Up" below "Slow Air"
- **THEN** the browser sends the order "Mossy Banks", "Slow Air", "Knees Up" to `PUT /api/playlists/{id}/tracks`
- **AND** the page lists the tracks in that order

#### Scenario: Drop where it started
- **WHEN** the user drops a dragged track back where it was
- **THEN** no request is sent

#### Scenario: Save fails
- **GIVEN** the server answers the reorder request with an error
- **WHEN** the user drags a track to a new position
- **THEN** the page lists the tracks in their previous order and shows an error message

#### Scenario: Buttons reach the same result
- **WHEN** the user activates "Move down" on the first track using only the keyboard
- **THEN** the tracks are reordered and saved as with a drag

### Requirement: Add a track to a playlist from the track list
The UI SHALL offer, on every track in the track list when a user is logged in, an "Add to playlist" control listing
the user's playlists, with a way to create a new playlist from it, and SHALL confirm when the track was added.

#### Scenario: Add from the list
- **GIVEN** the user is logged in and has the playlist "Session tunes"
- **WHEN** the user chooses "Add to playlist" then "Session tunes" on "Knees Up"
- **THEN** the browser sends `PUT /api/playlists/{id}/tracks/{trackId}` and the page confirms the track was added

#### Scenario: Anonymous visitor
- **GIVEN** `GET /api/me` responds 401 Unauthorized
- **WHEN** a visitor opens `/tracks`
- **THEN** no "Add to playlist" control is shown

### Requirement: Play a playlist
The UI SHALL let the user play a playlist from its page, starting at its first track, or from any track in it. The
player SHALL move on to the next track of the playlist when one ends, and stop after the last.

#### Scenario: Play all
- **GIVEN** a playlist holds "Knees Up" then "Mossy Banks"
- **WHEN** the user activates "Play" on the playlist page
- **THEN** the player loads "Knees Up"

#### Scenario: Continue to the next track
- **GIVEN** the player is playing "Knees Up", the first track of that playlist
- **WHEN** "Knees Up" ends
- **THEN** the player loads and plays "Mossy Banks"

#### Scenario: Last track ends
- **GIVEN** the player is playing the last track of the playlist
- **WHEN** it ends
- **THEN** the player stops and loads nothing else
