# comment Specification

## Purpose
Let Accounts comment on Tracks, Tunes and Sets: public to read, flat plain text, changed only by the author or an Administrator, and removed with the Track.

## Requirements

### Requirement: Read comments on a subject
The system SHALL return, on `GET /api/tracks/{id}/comments`, `GET /api/tunes/{id}/comments` and
`GET /api/sets/{id}/comments`, the Comments left on that Track, Tune or Set as a JSON array, oldest first, each with
its `id`, `text`, `authorId`, `authorName`, `createdAt` and `editedAt` (null when never edited). The endpoints SHALL
answer to anonymous requests. A subject that does not exist SHALL answer 404 Not Found.

#### Scenario: Anonymous reader
- **GIVEN** alice commented "Lovely flute" on the Track "Knees Up"
- **WHEN** an anonymous client requests `GET /api/tracks/{id}/comments`
- **THEN** the response is 200 with one Comment whose text is "Lovely flute" and whose authorName is alice's

#### Scenario: Oldest first
- **GIVEN** alice commented "First" and then bob commented "Second" on a Tune
- **WHEN** a client requests `GET /api/tunes/{id}/comments`
- **THEN** "First" is listed before "Second"

#### Scenario: Unknown subject
- **WHEN** a client requests `GET /api/sets/unknown/comments`
- **THEN** the response is 404 Not Found

### Requirement: Post a comment
The system SHALL let an authenticated Account post a Comment with `POST /api/{tracks|tunes|sets}/{id}/comments` and a
JSON body `{"text": "..."}`, answering 201 Created with the Comment. The text is plain text: it SHALL be trimmed,
SHALL NOT be blank, SHALL NOT exceed 2000 characters, and SHALL be shown verbatim, never interpreted as markup. An
invalid text SHALL answer 400 Bad Request, an unknown subject 404 Not Found and an unauthenticated request 401
Unauthorized.

#### Scenario: Post on a Set
- **GIVEN** alice is authenticated and the Set "Kesh and Morrison's" exists
- **WHEN** alice sends `POST /api/sets/{id}/comments` with `{"text": "  Great pairing "}`
- **THEN** the response is 201 Created with the text "Great pairing" and alice as author
- **AND** `GET /api/sets/{id}/comments` lists it

#### Scenario: Blank text
- **WHEN** alice posts the text "   "
- **THEN** the response is 400 Bad Request and nothing is stored

#### Scenario: Text too long
- **WHEN** alice posts a text of 2001 characters
- **THEN** the response is 400 Bad Request

#### Scenario: Markup is not interpreted
- **WHEN** alice posts `<b>hi</b>` and the Tune page lists it
- **THEN** the page shows the characters `<b>hi</b>` as text

#### Scenario: Anonymous post
- **WHEN** a client without a session or token sends `POST /api/tracks/{id}/comments`
- **THEN** the response is 401 Unauthorized

### Requirement: Edit and delete a comment
The system SHALL let the author of a Comment, or an Administrator, edit its text with `PATCH /api/comments/{id}` (same
text rules as posting; it sets `editedAt`) and delete it with `DELETE /api/comments/{id}` (204 No Content). Any
other Account SHALL get 403 Forbidden, an unauthenticated request 401 Unauthorized and an unknown Comment 404 Not
Found.

#### Scenario: Author edits
- **GIVEN** alice posted a Comment
- **WHEN** alice sends `PATCH /api/comments/{id}` with a new text
- **THEN** the response is 200 with the new text and a non-null `editedAt`

#### Scenario: Administrator deletes
- **GIVEN** alice posted a Comment and bob is an Administrator
- **WHEN** bob sends `DELETE /api/comments/{id}`
- **THEN** the response is 204 No Content and the Comment is no longer listed

#### Scenario: Another Account is refused
- **GIVEN** alice posted a Comment
- **WHEN** carol, who is not an Administrator, sends `DELETE /api/comments/{id}`
- **THEN** the response is 403 Forbidden and the Comment is still listed

### Requirement: Comments leave with their subject
The system SHALL delete every Comment on a Track when that Track is deleted.

#### Scenario: Track deleted
- **GIVEN** the Track "Knees Up" has two Comments
- **WHEN** the Track is deleted
- **THEN** its Comments no longer exist

### Requirement: Comments in the UI
The UI SHALL show, on the Tune and Set detail pages and in a Track's expanded details in the tracks list, the Comments of the subject with author and date, oldest
first. A signed-in Account SHALL see a form to post, and edit and delete actions on its own Comments (all Comments
for an Administrator); an anonymous visitor SHALL see the Comments and an invitation to sign in. The section SHALL be
keyboard operable and labelled for assistive technology, and use theme tokens.

#### Scenario: Anonymous visitor
- **GIVEN** a Tune has a Comment
- **WHEN** an anonymous visitor opens the Tune page
- **THEN** the Comment is shown, there is no post form, and a sign-in link is offered

#### Scenario: Posting
- **GIVEN** alice is signed in and has expanded a Track in the tracks list
- **WHEN** she types "Lovely flute" and submits
- **THEN** the Comment appears in the list and the form is cleared

#### Scenario: Own actions only
- **GIVEN** alice is signed in and the page lists her Comment and bob's
- **THEN** edit and delete are offered on hers only
