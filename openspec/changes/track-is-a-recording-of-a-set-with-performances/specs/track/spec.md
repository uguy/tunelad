## MODIFIED Requirements

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

## ADDED Requirements

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
