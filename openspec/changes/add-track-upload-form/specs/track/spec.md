# Spec Delta

## MODIFIED Requirements

### Requirement: Upload a track
The system SHALL accept a multipart upload on `POST /api/tracks` from an authenticated user, with the parts `title`, `description` and `file` (the audio file), and the optional parts `artist` and `album`, store the track with its audio and the uploader as its owner, and respond 201 Created with the stored track as a JSON object and a `Location` header giving the track's URL `/api/tracks/{id}`.

#### Scenario: Upload with all metadata
- **WHEN** an authenticated client uploads an mp3 file with a title, description, artist and album
- **THEN** the response status is 201 Created
- **AND** the body is the stored track with its `id`, that title, description, artist and album, format `MP3` and the uploader as `owner`
- **AND** the `Location` header is `/api/tracks/{id}` with that track's id
- **AND** listing tracks returns a track with that title, description, artist and album, and format `MP3`

#### Scenario: Upload without optional metadata
- **WHEN** an authenticated client uploads an mp3 file with only a title and description
- **THEN** the response status is 201 Created
- **AND** the stored track has that title and description, and no artist or album

#### Scenario: Created track can be fetched from its location
- **GIVEN** an authenticated client has uploaded a track
- **WHEN** the client requests the URL in the response's `Location` header
- **THEN** the response status is 200 OK and the body is the same track as the upload's response body

#### Scenario: Required part missing
- **WHEN** an authenticated client uploads a track without a `title`, a `description` or a `file` part
- **THEN** the response status is 400 Bad Request
- **AND** no track is stored

#### Scenario: Blank title or description
- **WHEN** an authenticated client uploads an mp3 file with a blank `title` or a blank `description`
- **THEN** the response status is 400 Bad Request
- **AND** no track is stored

## ADDED Requirements

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
The upload page SHALL show a form with a required title, description and audio file, and an optional artist and album; the file picker SHALL offer the accepted formats. Submitting SHALL send the multipart upload with the CSRF token. Once the server accepts it, the UI SHALL go to the tracks page and show the new track at once.

#### Scenario: Successful upload
- **GIVEN** alice is logged in and on the upload page
- **WHEN** alice fills in the title "Knees Up" and a description, chooses the file `knees-up.mp3` and submits
- **THEN** the browser sends `POST /api/tracks` with the title, description and file, and the `X-XSRF-TOKEN` header
- **AND** after the server answers 201 Created, the tracks page is shown and lists "Knees Up", even if search does not return it yet

#### Scenario: Required field left empty
- **GIVEN** alice is on the upload page
- **WHEN** alice submits the form without a title, a description or a file
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
