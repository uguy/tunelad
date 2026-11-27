# track Specification

## Purpose
Manage the track library through the REST API and the UI: upload a track with its audio file and metadata, browse and fetch tracks, and let a track's owner or an administrator edit its metadata or delete it. Playing the stored audio is covered by the `media` capability.

## Requirements

### Requirement: Upload a track
The system SHALL accept a multipart upload on `POST /api/tracks` from an authenticated user, with the parts `title`, `description`, `setId` (the id of an
existing Set) and `file` (the audio file), and the optional parts `artist`, `album` and `performances`, store the track with its audio, its Set and the uploader
as its owner, and respond 201 Created with the stored track as a JSON object and a `Location` header giving the track's URL `/api/tracks/{id}`. The track SHALL
have one Performance per Tune of its Set, in the Set's order. `performances` is a JSON array with one object per Tune, in Set order, each with optional
`startOffsetSeconds` (a number of at least 0, default 0), `key` (text of at most 30 characters) and `notes` (text of at most 2000 characters); when it is omitted every Performance takes the defaults.

#### Scenario: Upload with all metadata
- **GIVEN** a Set of the Tunes "Cooley's" then "The Kesh" exists
- **WHEN** an authenticated client uploads an mp3 file with a title, description, artist, album and that `setId`
- **THEN** the response status is 201 Created
- **AND** the body is the stored track with its `id`, that title, description, artist and album, format `MP3`, the uploader as `owner` and that Set as `set`
- **AND** the `Location` header is `/api/tracks/{id}` with that track's id
- **AND** listing tracks returns a track with that title, description, artist and album, and format `MP3`

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
- **WHEN** an authenticated client uploads a track without a `title`, a `description`, a `setId` or a `file` part
- **THEN** the response status is 400 Bad Request
- **AND** no track is stored

#### Scenario: Blank title or description
- **WHEN** an authenticated client uploads an mp3 file with a blank `title` or a blank `description`
- **THEN** the response status is 400 Bad Request
- **AND** no track is stored

#### Scenario: Unknown Set
- **WHEN** an authenticated client uploads an mp3 file with a `setId` no Set has
- **THEN** the response status is 400 Bad Request
- **AND** no track is stored

#### Scenario: Invalid performances
- **GIVEN** a Set of two Tunes exists
- **WHEN** an authenticated client uploads a track for it with `performances` of one entry or three entries, a negative start offset, a key over 30 characters, notes over 2000 characters, or text that is not a JSON array
- **THEN** the response status is 400 Bad Request
- **AND** no track is stored

### Requirement: Assign a unique identifier to each track
The system SHALL give every stored track a unique `id`, which identifies it in all other track endpoints.

#### Scenario: Two uploads with the same title
- **WHEN** a client uploads two tracks with the same title
- **THEN** listing tracks returns two tracks with different ids

### Requirement: List tracks
The system SHALL return all stored tracks as a JSON array on `GET /api/tracks`, each with its `id`, `title`, `description`, `artist`, `album`, `format` and `owner`.

#### Scenario: Library with tracks
- **GIVEN** two tracks have been uploaded
- **WHEN** a client requests `GET /api/tracks`
- **THEN** the response is a JSON array containing both tracks with their metadata and owner

#### Scenario: Empty library
- **GIVEN** no track has been uploaded
- **WHEN** a client requests `GET /api/tracks`
- **THEN** the response is an empty JSON array

### Requirement: Get a track by id
The system SHALL return the track with the given id as a JSON object on `GET /api/tracks/{id}`, and respond 404 Not Found when no track has that id.

#### Scenario: Existing track
- **GIVEN** a track has been uploaded
- **WHEN** a client requests `GET /api/tracks/{id}` with that track's id
- **THEN** the response status is 200 OK
- **AND** the body is that track with its metadata

#### Scenario: Unknown id
- **WHEN** a client requests `GET /api/tracks/{id}` with an id no track has
- **THEN** the response status is 404 Not Found

### Requirement: Uploading requires authentication
The system SHALL reject a track upload that carries neither a valid session nor a valid bearer token with 401 Unauthorized, without redirecting, and SHALL store nothing.

#### Scenario: Anonymous upload
- **WHEN** a client without a session or token uploads an mp3 file with a title and description
- **THEN** the response status is 401 Unauthorized and the response is not a redirect
- **AND** listing tracks returns no track with that title

### Requirement: Every track has an owner
The system SHALL record the account that uploaded a track as its owner, and SHALL include it in every track it returns as `owner`, an object with the account's `id` and `displayName` at upload time.

#### Scenario: Owner is the uploader
- **GIVEN** alice's account has id A and display name "Alice Liddell"
- **WHEN** alice uploads a track
- **THEN** that track in `GET /api/tracks` has `owner.id` A and `owner.displayName` "Alice Liddell"

#### Scenario: Owner on a single track
- **GIVEN** alice has uploaded a track
- **WHEN** a client requests `GET /api/tracks/{id}` for that track
- **THEN** the body has alice's account as `owner`

#### Scenario: Owners of different uploaders
- **GIVEN** alice and bob have each uploaded a track
- **WHEN** a client lists tracks
- **THEN** alice's track has alice as owner and bob's track has bob as owner

### Requirement: Browse tracks without logging in
The system SHALL serve `GET /api/tracks` and `GET /api/tracks/{id}` to anonymous clients exactly as to authenticated ones.

#### Scenario: Anonymous list
- **GIVEN** a track has been uploaded
- **WHEN** a client without a session or token requests `GET /api/tracks`
- **THEN** the response status is 200 OK and contains that track

#### Scenario: Anonymous get
- **GIVEN** a track has been uploaded
- **WHEN** a client without a session or token requests `GET /api/tracks/{id}` for it
- **THEN** the response status is 200 OK

### Requirement: Show who uploaded each track
The UI track list SHALL show, for each track, the display name of its owner.

#### Scenario: Uploader shown in the list
- **GIVEN** the track list contains "Knees Up" owned by "Alice Liddell"
- **WHEN** a visitor opens the tracks page
- **THEN** the "Knees Up" entry shows "Alice Liddell" as its uploader

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

### Requirement: Accepted audio formats
The system SHALL accept uploads whose file name ends in `.mp3`, `.m4a`, `.ogg`, `.flac` or `.wav`, case-insensitively, and store the track with format `MP3`, `M4A`, `OGG`, `FLAC` or `WAV` respectively. It SHALL refuse any other file with 415 Unsupported Media Type and store neither a track nor audio.

#### Scenario: Upload each supported format
- **WHEN** an authenticated client uploads a file named `tune.mp3`, `tune.m4a`, `tune.ogg`, `tune.flac` or `tune.wav` with a title and description
- **THEN** the response status is 201 Created
- **AND** the stored track's format is `MP3`, `M4A`, `OGG`, `FLAC` or `WAV` respectively

#### Scenario: Extension in upper case
- **WHEN** an authenticated client uploads a file named `TUNE.FLAC` with a title and description
- **THEN** the response status is 201 Created and the stored track's format is `FLAC`

#### Scenario: Unsupported file
- **WHEN** an authenticated client uploads a file named `notes.txt`, or a file name without an extension, with a title and description
- **THEN** the response status is 415 Unsupported Media Type
- **AND** listing tracks returns no track with that title

### Requirement: Upload size limit
The system SHALL refuse an upload whose audio file is larger than 100 MB (104,857,600 bytes) with 413 Content Too Large, and store neither a track nor audio. A file of exactly 100 MB SHALL be accepted.

#### Scenario: File over the limit
- **WHEN** an authenticated client uploads an mp3 file of 104,857,601 bytes with a title and description
- **THEN** the response status is 413 Content Too Large
- **AND** listing tracks returns no track with that title

#### Scenario: File at the limit
- **WHEN** an authenticated client uploads an mp3 file of 104,857,600 bytes with a title and description
- **THEN** the response status is 201 Created

### Requirement: A failed upload leaves nothing behind
When an upload is refused, fails while the audio is being stored, or is aborted by the client before it completes, the system SHALL store neither the track nor any of its audio, and SHALL not report the track as new to other parts of the system, such as search.

#### Scenario: Client aborts during the upload
- **GIVEN** an authenticated client has started uploading an mp3 file with the title "Half Way"
- **WHEN** the client closes the connection before the whole file has been sent
- **THEN** listing tracks returns no track titled "Half Way"
- **AND** no audio from that upload remains stored
- **AND** searching for "Half Way" returns no track, also after 5 seconds

#### Scenario: Storing the audio fails
- **GIVEN** storing audio fails for an upload titled "Broken"
- **WHEN** an authenticated client uploads an mp3 file titled "Broken"
- **THEN** the response has an error status
- **AND** listing tracks returns no track titled "Broken" and no audio from that upload remains stored

### Requirement: Upload link on the tracks page
The tracks page SHALL show logged-in users an "Upload" link to the upload page `/tracks/upload`, and SHALL show anonymous visitors a "Log in to upload" link to the login page instead.

#### Scenario: Logged-in user
- **GIVEN** alice is logged in
- **WHEN** alice opens the tracks page
- **THEN** an "Upload" link to `/tracks/upload` is shown

#### Scenario: Anonymous visitor
- **GIVEN** nobody is logged in
- **WHEN** a visitor opens the tracks page
- **THEN** no "Upload" link is shown and a "Log in to upload" link is shown

#### Scenario: Anonymous visitor opens the upload page
- **GIVEN** nobody is logged in
- **WHEN** a visitor opens `/tracks/upload` directly
- **THEN** no upload form is shown and a "Log in to upload" link is shown

### Requirement: Upload a track from the UI
The upload page SHALL show a form with a required title, description, Set and audio file, and an optional artist and album; the file picker SHALL offer the accepted
formats. The Set field SHALL let the Account choose an existing Set from a list of Sets (named by their Tunes) or build a new one by searching Tunes, adding,
reordering and removing them. For each Tune of the chosen Set the form SHALL show optional fields for the Performance's start offset, key and notes. Submitting
SHALL create the Set when it is a new one, then send the multipart upload with its `setId` and `performances` and the CSRF token. Once the server accepts it, the UI SHALL go to the
tracks page and show the new track at once.

#### Scenario: Successful upload
- **GIVEN** alice is logged in and on the upload page, and the Set "Cooley's, The Kesh" exists
- **WHEN** alice fills in the title "Knees Up" and a description, chooses the Set "Cooley's, The Kesh" and the file `knees-up.mp3`, enters the key "Em" for "Cooley's" and submits
- **THEN** the browser sends `POST /api/tracks` with the title, description, `setId`, `performances` and file, and the `X-XSRF-TOKEN` header
- **AND** after the server answers 201 Created, the tracks page is shown and lists "Knees Up", even if search does not return it yet

#### Scenario: Upload with a new Set
- **GIVEN** alice is on the upload page
- **WHEN** alice builds the Set "Cooley's, The Kesh" from Tune search, fills in the other required fields and submits
- **THEN** the browser sends `POST /api/sets` with those Tune ids in order, then `POST /api/tracks` with the returned Set's id

#### Scenario: Performance fields follow the Set
- **GIVEN** alice is on the upload page
- **WHEN** alice chooses a Set of two Tunes
- **THEN** one group of Performance fields is shown for each Tune, labelled with its name, in Set order

#### Scenario: Required field left empty
- **GIVEN** alice is on the upload page
- **WHEN** alice submits the form without a title, a description, a Set or a file
- **THEN** no request is sent and the form shows which field is missing

### Requirement: Upload progress and cancel in the UI
While a file uploads, the upload page SHALL show the share of the file sent so far as a progress bar, prevent a second submission, and offer a "Cancel" control. Cancelling SHALL abort the request and return to the form with its fields and file kept.

#### Scenario: Progress shown
- **GIVEN** alice has submitted an upload
- **WHEN** part of the file has been sent
- **THEN** a progress bar shows the share sent so far
- **AND** the submit control is disabled

#### Scenario: Cancel an upload
- **GIVEN** alice's upload is in progress
- **WHEN** alice activates "Cancel"
- **THEN** the request is aborted and the page says that the upload was cancelled
- **AND** the form still holds alice's title, description, artist, album and chosen file

### Requirement: Failed upload handling in the UI
When an upload fails, the upload page SHALL show a message that says why and keep every field and the chosen file, so that submitting again retries the same upload. Before sending, the page SHALL refuse a file over 100 MB or of an unsupported format with the same message the server's refusal would show.

#### Scenario: Server refuses the upload
- **GIVEN** alice has filled in the form and chosen a file
- **WHEN** the server answers 413, 415, 400, 401, 403 or another error status, or the connection fails
- **THEN** the page shows the message for that case: file too large (stating the 100 MB limit), unsupported format (listing mp3, m4a, ogg, flac and wav), missing title or description, session expired with a link to log in again, not allowed, or a server or connection error inviting a retry
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

### Requirement: An Artist is a shared record matched by name
The system SHALL give a Track that names an artist an Artist shared by every Track naming the same artist. Names SHALL match ignoring case and surrounding whitespace. The first Track to use a name SHALL create the Artist with that name, spelled as given after trimming, and later Tracks SHALL reuse it.

#### Scenario: First use creates the Artist
- **GIVEN** no Track names the artist "Altan"
- **WHEN** an authenticated client uploads a track with the artist "Altan"
- **THEN** the stored track's `artist` is "Altan"

#### Scenario: Same name reuses the Artist
- **GIVEN** a track has been uploaded with the artist "Altan"
- **WHEN** an authenticated client uploads a second track with the artist "Altan"
- **THEN** both tracks have the artist "Altan"
- **AND** the system holds one Artist named "Altan"

#### Scenario: Name matches ignoring case and whitespace
- **GIVEN** a track has been uploaded with the artist "Altan"
- **WHEN** an authenticated client uploads a track with the artist "  altan "
- **THEN** the new track's `artist` is "Altan"
- **AND** the system still holds one Artist named "Altan"

#### Scenario: Different names are different Artists
- **WHEN** tracks are uploaded with the artists "Altan" and "Dervish"
- **THEN** the system holds two Artists

#### Scenario: Blank artist means no Artist
- **WHEN** an authenticated client uploads a track with a blank `artist`, or with none
- **THEN** the stored track has no artist
- **AND** no Artist is created

### Requirement: Editing a track's artist uses the shared Artist
The system SHALL resolve the artist of an edited Track the same way as on upload: reuse the Artist matching the name, create it on first use, or remove the Track's Artist when the name is blank.

#### Scenario: Edit to an existing Artist's name
- **GIVEN** a track by "Altan" and another track by "Dervish"
- **WHEN** the owner of the second track edits its artist to "altan"
- **THEN** the edited track's `artist` is "Altan"
- **AND** the system holds Artists "Altan" and "Dervish"

#### Scenario: Edit to a new name
- **WHEN** the owner of a track edits its artist to a name no Track uses
- **THEN** the track's `artist` is that name

#### Scenario: Clear the artist
- **WHEN** the owner of a track by "Altan" edits its artist to blank
- **THEN** the track has no artist
- **AND** other tracks by "Altan" keep it

### Requirement: Existing tracks keep their artist
The system SHALL keep the artist name of every track stored before Artists existed, and SHALL share one Artist between the tracks whose names match.

#### Scenario: Migration of existing tracks
- **GIVEN** stored tracks with the artist names "Altan", "altan" and "Dervish"
- **WHEN** the system starts on the new version
- **THEN** each track still returns its artist, and the "Altan" and "altan" tracks return the same name

### Requirement: A track shows its Set and Performances
Every track the API returns (`GET /api/tracks`, `GET /api/tracks/{id}` and the responses of upload and edit) SHALL include `set`, the Set it records with its
Tunes in order, and `performances`, one object per Tune of that Set, in order, with `position` (from 1), `tuneId`, `tuneName`, `danceType`, `startOffsetSeconds`,
`key` and `notes`. A track stored before Sets existed SHALL have no `set` and an empty `performances`, and stay listed, playable and editable. The UI track page
SHALL show the Set's Tunes with their Performances' start offsets, keys and notes.

#### Scenario: Track with a Set
- **GIVEN** alice uploaded a track for the Set of "Cooley's" then "The Kesh"
- **WHEN** a client requests `GET /api/tracks/{id}`
- **THEN** `set` lists "Cooley's" then "The Kesh"
- **AND** `performances` has two entries whose `tuneName` are "Cooley's" and "The Kesh" and whose `danceType` are those of the Tunes

#### Scenario: Track from before Sets
- **GIVEN** a track was stored before Sets existed
- **WHEN** a client requests it
- **THEN** it has no `set`, `performances` is empty, and `GET /api/tracks/{id}/play` still returns its audio

#### Scenario: Tune renamed
- **GIVEN** a track has a Performance of the Tune "Cooley's"
- **WHEN** the Tune is renamed and a client requests the track
- **THEN** the Performance shows the new name

#### Scenario: Performances on the track page
- **GIVEN** a track whose second Performance has start offset 95.5, key "G" and notes "plays the B part twice"
- **WHEN** a visitor opens the track page
- **THEN** the page lists the Tunes in order, and shows 1:35 as that Performance's start, its key and its notes

### Requirement: Edit a track's Performances
`PUT /api/tracks/{id}` SHALL accept an optional `performances` array in its JSON body, with one object per Performance in position order, each with
`startOffsetSeconds`, `key` and `notes` under the same limits as at upload, and SHALL replace those values on the track's Performances. An omitted or null
`performances` SHALL leave them unchanged. The track's Set and the Tunes and order of its Performances SHALL stay unchanged. The rules of
"Only the owner or an administrator changes a track" apply. A track without a Set accepts only an omitted or empty `performances`.

#### Scenario: Owner edits a Performance
- **GIVEN** alice uploaded a track for the Set of "Cooley's" then "The Kesh"
- **WHEN** alice puts the same metadata and `performances` giving the second the key "Am" and start offset 120
- **THEN** the response status is 200 OK and the second Performance has key "Am" and start offset 120
- **AND** the Set and the order of the Performances are as before

#### Scenario: Performances omitted
- **GIVEN** a track has a Performance with key "Em"
- **WHEN** its owner puts new metadata without `performances`
- **THEN** the Performance still has key "Em"

#### Scenario: Wrong number of Performances or invalid values
- **GIVEN** a track of a Set of two Tunes
- **WHEN** its owner puts `performances` with one entry, three entries, a negative start offset, a key over 30 characters or notes over 2000 characters
- **THEN** the response status is 400 Bad Request and the Performances are unchanged

#### Scenario: Not the owner
- **GIVEN** alice uploaded a track and bob is neither an administrator nor its owner
- **WHEN** bob puts `performances` to it
- **THEN** the response status is 403 Forbidden and the Performances are unchanged

### Requirement: Deleting a track deletes its Performances
Deleting a track SHALL delete its Performances, and SHALL remove the track from every Playlist and Favourite as before. The Set and the Tunes SHALL stay.

#### Scenario: Delete a track with Performances
- **GIVEN** a track of the Set "Cooley's, The Kesh" is in bob's Playlist and Favourites
- **WHEN** its owner deletes it
- **THEN** the response status is 204 No Content
- **AND** its Performances no longer exist, the track is in no Playlist and no Favourite, and the Set and its Tunes still exist
