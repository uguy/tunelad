# Spec Delta

## MODIFIED Requirements

### Requirement: Upload a track
The system SHALL accept a multipart upload on `POST /api/tracks` from an authenticated user whose only required part is `file` (the audio file), with the optional parts `title`, `description`, `artist`, `album`, `setId` (the id of an existing Set) and `performances`. It SHALL store the track with its audio and the uploader as its owner, and respond 201 Created with the stored track as a JSON object and a `Location` header giving the track's URL `/api/tracks/{id}`. A blank text part SHALL count as omitted. A track uploaded without a `title` SHALL be titled with its file name without the extension, cut to 80 characters (`Untitled` when nothing is left). A track with a Set SHALL have one Performance per Tune of its Set, in the Set's order; a track without a Set has none. `performances` is a JSON array with one object per Tune, in Set order, each with optional `startOffsetSeconds` (a number of at least 0, default 0), `key` (text of at most 30 characters) and `notes` (text of at most 2000 characters); it needs a `setId`, and when it is omitted every Performance takes the defaults.

#### Scenario: Upload with all metadata
- **GIVEN** a Set of the Tunes "Cooley's" then "The Kesh" exists
- **WHEN** an authenticated client uploads an mp3 file with a title, description, artist, album and that `setId`
- **THEN** the response status is 201 Created
- **AND** the body is the stored track with its `id`, that title, description, artist and album, format `MP3`, the uploader as `owner` and that Set as `set`
- **AND** the `Location` header is `/api/tracks/{id}` with that track's id
- **AND** listing tracks returns a track with that title, description, artist and album, and format `MP3`

#### Scenario: Upload with only a file
- **WHEN** an authenticated client uploads a file named `knees-up.mp3` with no other part
- **THEN** the response status is 201 Created
- **AND** the stored track is titled "knees-up", has format `MP3` and the uploader as `owner`
- **AND** it has no description, artist, album or Set, and no Performances

#### Scenario: Upload without optional metadata
- **GIVEN** a Set exists
- **WHEN** an authenticated client uploads an mp3 file with only a title, description and `setId`
- **THEN** the response status is 201 Created
- **AND** the stored track has that title and description, and no artist or album

#### Scenario: Performances default
- **GIVEN** a Set of "Cooley's" then "The Kesh" exists
- **WHEN** an authenticated client uploads a track for it without `performances`
- **THEN** the track has two Performances, "Cooley's" at position 1 then "The Kesh" at position 2, each with start offset 0, no key and no notes

#### Scenario: Performances given
- **GIVEN** a Set of "Cooley's" then "The Kesh" exists
- **WHEN** an authenticated client uploads a track for it with `performances` giving start offset 0 and key "Em" for the first, and start offset 95.5, key "G" and notes "plays the B part twice" for the second
- **THEN** the track's Performances carry those values, in that order

#### Scenario: Created track can be fetched from its location
- **GIVEN** an authenticated client has uploaded a track
- **WHEN** the client requests the URL in the response's `Location` header
- **THEN** the response status is 200 OK and the body is the same track as the upload's response body

#### Scenario: Required part missing
- **WHEN** an authenticated client uploads a track with a title, a description and a `setId` but no `file` part
- **THEN** the response status is 400 Bad Request
- **AND** no track is stored

#### Scenario: Blank title or description
- **WHEN** an authenticated client uploads a file named `reel.mp3` with a blank `title`, a blank `description` and a blank `setId`
- **THEN** the response status is 201 Created
- **AND** the stored track is titled "reel" and has no description and no Set

#### Scenario: Long file name
- **WHEN** an authenticated client uploads a file whose name, without its extension, is 120 characters long, with no `title`
- **THEN** the response status is 201 Created
- **AND** the stored track's title is its first 80 characters

#### Scenario: Unknown Set
- **WHEN** an authenticated client uploads an mp3 file with a `setId` no Set has
- **THEN** the response status is 400 Bad Request
- **AND** no track is stored

#### Scenario: Invalid performances
- **GIVEN** a Set of two Tunes exists
- **WHEN** an authenticated client uploads a track for it with `performances` of one entry or three entries, a negative start offset, a key over 30 characters, notes over 2000 characters, or text that is not a JSON array
- **THEN** the response status is 400 Bad Request
- **AND** no track is stored

#### Scenario: Performances without a Set
- **WHEN** an authenticated client uploads an mp3 file with `performances` and no `setId`
- **THEN** the response status is 400 Bad Request
- **AND** no track is stored

### Requirement: Edit a track's metadata
The system SHALL replace the `title`, `description`, `artist` and `album` of the track with the given id with those of the JSON body on `PUT /api/tracks/{id}`, and respond 200 OK with the updated track. The `title` is required and must not be blank. An omitted, null or blank `description`, `artist` or `album` SHALL clear it. The track's `id`, `format`, `owner` and audio SHALL stay unchanged.

#### Scenario: Owner edits all metadata
- **GIVEN** alice has uploaded a track titled "Knees Up" with artist "Breabach"
- **WHEN** alice puts the title "Knees Up (live)", description "Recorded at Celtic Connections", artist "Breabach" and album "Live" to `/api/tracks/{id}`
- **THEN** the response status is 200 OK and the body is the track with that title, description, artist and album
- **AND** `GET /api/tracks/{id}` returns the same metadata, with the same `id`, `format` and `owner` as before

#### Scenario: Optional metadata omitted
- **GIVEN** alice has uploaded a track with a description, an artist and an album
- **WHEN** alice puts only a title to `/api/tracks/{id}`
- **THEN** the response status is 200 OK
- **AND** the track has no description, no artist and no album

#### Scenario: Audio unchanged after an edit
- **GIVEN** alice has uploaded a track with an mp3 file
- **WHEN** alice edits its metadata
- **THEN** `GET /api/tracks/{id}/play` still returns a body byte-for-byte identical to the uploaded file

#### Scenario: Required field missing or blank
- **GIVEN** alice has uploaded a track titled "Knees Up"
- **WHEN** alice puts a body with no `title` or a blank `title` to `/api/tracks/{id}`
- **THEN** the response status is 400 Bad Request
- **AND** the track is still titled "Knees Up"

#### Scenario: Edit an unknown track
- **WHEN** an authenticated client puts valid metadata to `/api/tracks/{id}` with an id no track has
- **THEN** the response status is 404 Not Found

### Requirement: Edit a track from the UI
Activating "Edit" on a track SHALL show a form with its title, description, artist and album, of which only the title is required. Saving SHALL send the edit with the CSRF token and show the updated track in the list as soon as the server accepts it. Cancelling SHALL leave the track unchanged. A rejected edit SHALL show an error and keep the track as it was.

#### Scenario: Save an edit
- **GIVEN** alice is logged in and the list shows her track "Knees Up"
- **WHEN** alice activates "Edit" on it, changes the title to "Knees Up (live)" and saves
- **THEN** the browser sends `PUT /api/tracks/{id}` with the new metadata and the `X-XSRF-TOKEN` header
- **AND** the list shows "Knees Up (live)" in place of "Knees Up"

#### Scenario: Save an edit with the description emptied
- **GIVEN** alice is editing her track "Knees Up", which has a description
- **WHEN** alice empties the description and saves
- **THEN** the browser sends the edit and the list shows the track without a description

#### Scenario: Cancel an edit
- **GIVEN** alice is editing her track "Knees Up"
- **WHEN** alice changes the title and cancels
- **THEN** no request is sent and the list still shows "Knees Up"

#### Scenario: Edit rejected
- **GIVEN** alice is editing her track "Knees Up"
- **WHEN** alice saves and the server answers 403 Forbidden
- **THEN** an error message is shown
- **AND** the list still shows "Knees Up"

### Requirement: Upload a track from the UI
The upload page SHALL show a form that asks for the audio file first, which is the only required field, followed by an optional title, description, artist, album and Set; the file picker SHALL offer the accepted formats, and the form SHALL say that Tune Lad fills in what it can from the file. The Set field SHALL default to "No set yet" and let the Account choose an existing Set from a list of Sets (named by their Tunes) or build a new one by searching Tunes, adding, reordering and removing them. For each Tune of a chosen Set the form SHALL show optional fields for the Performance's start offset, key and notes. Submitting SHALL create the Set when it is a new one, then send the multipart upload with the fields that are filled in, its `setId` and `performances` when there is a Set, and the CSRF token. Once the server accepts it, the UI SHALL go to the tracks page and show the new track at once.

#### Scenario: Successful upload
- **GIVEN** alice is logged in and on the upload page, and the Set "Cooley's, The Kesh" exists
- **WHEN** alice fills in the title "Knees Up" and a description, chooses the Set "Cooley's, The Kesh" and the file `knees-up.mp3`, enters the key "Em" for "Cooley's" and submits
- **THEN** the browser sends `POST /api/tracks` with the title, description, `setId`, `performances` and file, and the `X-XSRF-TOKEN` header
- **AND** after the server answers 201 Created, the tracks page is shown and lists "Knees Up", even if search does not return it yet

#### Scenario: Upload with only a file
- **GIVEN** alice is on the upload page
- **WHEN** alice chooses the file `knees-up.mp3` and submits without filling in anything else
- **THEN** the browser sends `POST /api/tracks` with the file and no other part, and the `X-XSRF-TOKEN` header
- **AND** after the server answers 201 Created, the tracks page is shown and lists the track returned by the server

#### Scenario: Upload with a new Set
- **GIVEN** alice is on the upload page
- **WHEN** alice builds the Set "Cooley's, The Kesh" from Tune search, chooses a file and submits
- **THEN** the browser sends `POST /api/sets` with those Tune ids in order, then `POST /api/tracks` with the returned Set's id

#### Scenario: Performance fields follow the Set
- **GIVEN** alice is on the upload page
- **WHEN** alice chooses a Set of two Tunes
- **THEN** one group of Performance fields is shown for each Tune, labelled with its name, in Set order
- **AND** choosing "No set yet" again hides them

#### Scenario: Only the file is marked required
- **GIVEN** alice is on the upload page
- **THEN** the audio file field is marked required, and the title, description, artist, album and Set fields are labelled optional
- **AND** a sentence says that Tune Lad fills in what it can from the file

#### Scenario: Required field left empty
- **GIVEN** alice is on the upload page
- **WHEN** alice submits the form without a file
- **THEN** no request is sent and the form shows that the file is missing

### Requirement: Failed upload handling in the UI
When an upload fails, the upload page SHALL show a message that says why and keep every field and the chosen file, so that submitting again retries the same upload. Before sending, the page SHALL refuse a file over 100 MB or of an unsupported format with the same message the server's refusal would show.

#### Scenario: Server refuses the upload
- **GIVEN** alice has chosen a file and filled in what she wants
- **WHEN** the server answers 413, 415, 400, 401, 403 or another error status, or the connection fails
- **THEN** the page shows the message for that case: file too large (stating the 100 MB limit), unsupported format (listing mp3, m4a, ogg, flac, wav and wma), a set or performances that do not fit, session expired with a link to log in again, not allowed, or a server or connection error inviting a retry
- **AND** the form still holds alice's fields and chosen file

#### Scenario: Retry after a failure
- **GIVEN** alice's upload failed with a connection error and the form still holds her input
- **WHEN** alice submits again and the server answers 201 Created
- **THEN** the tracks page is shown and lists the new track

#### Scenario: File refused before sending
- **GIVEN** alice is on the upload page
- **WHEN** alice chooses a file larger than 100 MB, or a file whose name does not end in a supported extension, and submits
- **THEN** no request is sent
- **AND** the page shows the file too large or unsupported format message

## ADDED Requirements

### Requirement: A track may have no Set
The system SHALL treat a track without a Set like any other track: it is listed, fetched, edited, played and deleted as usual, its `set` is absent and its `performances` is empty. The UI SHALL show such a track without a Tunes section.

#### Scenario: Fetch a track without a Set
- **GIVEN** a track was uploaded with only a file
- **WHEN** a client requests `GET /api/tracks/{id}`
- **THEN** the response status is 200 OK, the body has no `set` and its `performances` is empty

#### Scenario: Play, edit and delete a track without a Set
- **GIVEN** alice uploaded a track with only a file
- **WHEN** a client plays it and alice edits its title and then deletes it
- **THEN** each request succeeds as for a track with a Set

#### Scenario: A track without a Set in the list
- **GIVEN** the tracks page lists a track that has no Set, no description and no artist
- **WHEN** the track's details are opened
- **THEN** no Tunes section and no empty artist, album or description lines are shown
