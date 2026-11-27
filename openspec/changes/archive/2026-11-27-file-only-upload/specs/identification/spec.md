# Spec Delta

## ADDED Requirements

### Requirement: Tags fill the blank metadata
After a Track is saved, the system SHALL read the title, artist, album and comment tags of its audio in the background, without delaying the upload response, and set each of the Track's title, artist, album and description that no Account has provided from the matching tag (the comment gives the description) when that tag is present and not blank. A title from a tag replaces the title taken from the file name. Tags SHALL be read for every accepted audio format.

#### Scenario: Tags fill an upload with only a file
- **GIVEN** an mp3 file tagged with title "Rainfall", artist "Breabach", album "Live" and comment "Rain on tin"
- **WHEN** an authenticated client uploads it with no other part
- **THEN** the response status is 201 Created, before the tags are read
- **AND** shortly after, the Track is titled "Rainfall" with artist "Breabach", album "Live" and description "Rain on tin"

#### Scenario: Tags of each format
- **GIVEN** an m4a, an ogg, a flac and a wma file, each tagged with a title
- **WHEN** each is uploaded with no other part
- **THEN** shortly after, each Track is titled with its file's tag title

#### Scenario: Only some tags present
- **GIVEN** an mp3 file tagged with an artist only
- **WHEN** it is uploaded as `reel.mp3` with no other part
- **THEN** shortly after, the Track has that artist, is still titled "reel" and has no album and no description

#### Scenario: The Artist is the shared record
- **GIVEN** an Artist named "Breabach" exists
- **WHEN** a file tagged with the artist " breabach " is uploaded
- **THEN** the Track's Artist is that existing one

#### Scenario: Blank tags are ignored
- **GIVEN** a file whose title and album tags are blank
- **WHEN** it is uploaded as `reel.mp3` with no other part
- **THEN** the Track is still titled "reel" and has no album

#### Scenario: A long tag title
- **GIVEN** a file whose title tag is 120 characters long
- **WHEN** it is uploaded with no other part
- **THEN** shortly after, the Track's title is its first 80 characters

### Requirement: Values an Account provided are never overwritten by tags
A title, description, artist or album that an Account provided SHALL never be replaced by a tag. A field counts as provided when the uploader gave it at upload, and all four count as provided once an Account has saved an edit of the Track.

#### Scenario: The uploader's title wins
- **GIVEN** a file tagged with the title "Rainfall"
- **WHEN** an authenticated client uploads it with the title "Wet Tuesday" and no other metadata
- **THEN** the Track keeps the title "Wet Tuesday", whatever the tags say
- **AND** its blank artist, album and description are filled from the tags

#### Scenario: An edit before the tags are read
- **GIVEN** a file tagged with an artist and uploaded with only the file
- **WHEN** its owner edits the Track, leaving the artist empty, before the tags are read
- **THEN** the Track still has no artist once the tags have been read

#### Scenario: An edit after the tags were read
- **GIVEN** the tags of a Track have been read and its owner then changes its title
- **WHEN** the upload's event is delivered again, as after a restart
- **THEN** the title the owner saved is unchanged

### Requirement: Unreadable tags leave the track as it is
A file without tags, a file whose tags cannot be read, and a tag reader that is not available (such as ffprobe missing) SHALL leave the Track as it was, usable and with its file-name title, and SHALL NOT fail the upload or the other Identification.

#### Scenario: File without tags
- **GIVEN** a file with no tags
- **WHEN** it is uploaded as `reel.mp3` with no other part
- **THEN** the Track is titled "reel" and nothing else changes

#### Scenario: Tags cannot be read
- **GIVEN** a file whose audio cannot be read
- **WHEN** it is uploaded with no other part
- **THEN** the response status is 201 Created and the Track is titled after its file name

### Requirement: A track without a Set is not identified
A Track without a Set SHALL NOT be sent to the analysis service. A request to run Identification again on such a Track SHALL be accepted and change nothing.

#### Scenario: Upload without a Set
- **GIVEN** the analysis service records the requests it receives
- **WHEN** an authenticated client uploads a file with no `setId`
- **THEN** the analysis service receives no request for that Track

#### Scenario: Re-run on a track without a Set
- **GIVEN** a Track without a Set
- **WHEN** its Owner requests `POST /api/tracks/{id}/identification`
- **THEN** the response status is 202 Accepted and the Track is unchanged
