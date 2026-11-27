# favourite Specification

## Purpose
TBD - created by archiving change redesign-ui-favourites-playlists. Update Purpose after archive.

## Requirements

### Requirement: Mark and unmark a favourite track
The system SHALL let an authenticated user mark a track as a favourite with `PUT /api/tracks/{id}/favourite` and
remove the mark with `DELETE /api/tracks/{id}/favourite`. Both operations SHALL be idempotent and answer 204 No
Content. Marking a track that does not exist SHALL answer 404 Not Found, and an unauthenticated request SHALL
answer 401 Unauthorized. Favourites belong to the account that made them: they are not visible to other users.

#### Scenario: Mark a favourite
- **GIVEN** alice is authenticated and the track "Knees Up" exists
- **WHEN** alice sends `PUT /api/tracks/{id}/favourite`
- **THEN** the response status is 204 No Content
- **AND** "Knees Up" is in alice's favourites

#### Scenario: Marking twice changes nothing
- **GIVEN** alice has already marked "Knees Up" as a favourite
- **WHEN** alice sends `PUT /api/tracks/{id}/favourite` again
- **THEN** the response status is 204 No Content
- **AND** "Knees Up" appears once in her favourites

#### Scenario: Unmark a favourite
- **GIVEN** alice has marked "Knees Up" as a favourite
- **WHEN** alice sends `DELETE /api/tracks/{id}/favourite`
- **THEN** the response status is 204 No Content
- **AND** "Knees Up" is no longer in her favourites

#### Scenario: Unmarking a track that is not a favourite
- **GIVEN** "Knees Up" is not among alice's favourites
- **WHEN** alice sends `DELETE /api/tracks/{id}/favourite`
- **THEN** the response status is 204 No Content

#### Scenario: Unknown track
- **WHEN** alice sends `PUT /api/tracks/unknown/favourite`
- **THEN** the response status is 404 Not Found

#### Scenario: Anonymous request
- **WHEN** a client without a session or token sends `PUT /api/tracks/{id}/favourite`
- **THEN** the response status is 401 Unauthorized

#### Scenario: Favourites are private
- **GIVEN** alice has marked "Knees Up" as a favourite
- **WHEN** bob requests `GET /api/me/favourites`
- **THEN** "Knees Up" is not in the response

### Requirement: List my favourite tracks
The system SHALL return, on `GET /api/me/favourites`, the authenticated user's favourite tracks as a JSON array of
tracks with the same fields as the track list, most recently marked first. It SHALL answer 401 Unauthorized, without
redirecting, when the request is not authenticated.

#### Scenario: Most recent first
- **GIVEN** alice marked "Knees Up" and then "Mossy Banks" as favourites
- **WHEN** alice requests `GET /api/me/favourites`
- **THEN** the response lists "Mossy Banks" before "Knees Up"

#### Scenario: No favourites
- **GIVEN** bob has marked no favourite
- **WHEN** bob requests `GET /api/me/favourites`
- **THEN** the response status is 200 OK and the body is an empty array

#### Scenario: Anonymous request
- **WHEN** a client without a session or token requests `GET /api/me/favourites`
- **THEN** the response status is 401 Unauthorized and the response is not a redirect

### Requirement: A deleted track leaves every favourites list
The system SHALL remove a track from every user's favourites when the track is deleted.

#### Scenario: Track deleted
- **GIVEN** alice and bob have both marked "Knees Up" as a favourite
- **WHEN** the track "Knees Up" is deleted
- **THEN** neither alice's nor bob's favourites contain it

### Requirement: Favourites page
The UI SHALL show the user's favourite tracks on `/tracks/favourites`, in the same track list as the Tracks page, so
they can be played, and SHALL show an empty state that points to the Tracks page when there are none. For an
anonymous visitor the page SHALL offer a link to log in instead of a list.

#### Scenario: Favourites listed
- **GIVEN** `GET /api/me/favourites` returns "Knees Up" and "Mossy Banks"
- **WHEN** the user opens `/tracks/favourites`
- **THEN** both tracks are listed and can be played

#### Scenario: Empty state
- **GIVEN** `GET /api/me/favourites` returns an empty array
- **WHEN** the user opens `/tracks/favourites`
- **THEN** the page says there are no favourites yet and links to `/tracks`

#### Scenario: Anonymous visitor
- **GIVEN** `GET /api/me` responds 401 Unauthorized
- **WHEN** a visitor opens `/tracks/favourites`
- **THEN** the page offers a "Log in" link instead of a track list

### Requirement: Favourite control on tracks
The UI SHALL show a favourite toggle on every track in the track list and in the player bar when a user is logged
in, reflecting whether the track is one of their favourites, and SHALL not show it to anonymous visitors. Activating
it SHALL mark or unmark the track and update the toggle immediately, and SHALL restore it and tell the user if the
server refuses.

#### Scenario: Mark from the list
- **GIVEN** the user is logged in and "Knees Up" is not a favourite
- **WHEN** the user activates the favourite toggle of "Knees Up"
- **THEN** the browser sends `PUT /api/tracks/{id}/favourite`
- **AND** the toggle shows "Knees Up" as a favourite

#### Scenario: Server refuses
- **GIVEN** the user is logged in and the server answers the favourite request with an error
- **WHEN** the user activates the favourite toggle
- **THEN** the toggle returns to its previous state and an error message is shown

#### Scenario: Anonymous visitor
- **GIVEN** `GET /api/me` responds 401 Unauthorized
- **WHEN** a visitor opens `/tracks`
- **THEN** no favourite toggle is shown
