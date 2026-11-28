## ADDED Requirements

### Requirement: An Album groups Tracks by one Artist
The system SHALL store Albums, each with a title, one Artist, an optional cover and an optional release date, and SHALL let
an authenticated Account create one by hand with `POST /api/albums`, read one with `GET /api/albums/{id}` (listing its
Tracks in order) and list Albums with `GET /api/albums`, without authentication. A title SHALL NOT be blank.

#### Scenario: Create an Album by hand
- **WHEN** alice sends `POST /api/albums` with title "At the End of the Day" and Artist "Dervish"
- **THEN** the response status is 201 Created and `GET /api/albums/{id}` returns it with no Tracks

#### Scenario: Blank title
- **WHEN** alice sends `POST /api/albums` with a blank title
- **THEN** the response status is 400 Bad Request

### Requirement: A Catalogue Track has no audio yet
The system SHALL support Tracks that have no audio, called Catalogue Tracks, with a place on an Album. A Catalogue Track
SHALL be listed and fetched with `catalogue` true, SHALL have no audio stream (`GET /api/tracks/{id}/audio` answers 404),
and the UI SHALL show it with the text "No audio yet" and no player.

#### Scenario: Fetch a Catalogue Track
- **GIVEN** an Album has a Catalogue Track "Cooley's"
- **WHEN** a client requests `GET /api/tracks/{id}`
- **THEN** the response status is 200 OK with `catalogue` true and its Album

#### Scenario: No audio to play
- **WHEN** a client requests `GET /api/tracks/{id}/audio` of a Catalogue Track
- **THEN** the response status is 404 Not Found

### Requirement: Import Albums from thesession.org recordings
The system SHALL let an Administrator start an import with `POST /api/albums/import-dump`, which downloads `recordings.csv`
from adactio/TheSession-data at run time and answers 202 Accepted. For each recording it SHALL create the Album (matched by
Artist and title ignoring case) with one Catalogue Track per track, and one Performance per listed tune in order. The Artist
SHALL be found or created unclassified. A tune whose thesession.org id is in the Tune catalogue SHALL resolve to that Tune;
otherwise the Performance SHALL be unresolved and keep the listed name. A Track SHALL get a Set only when all its
Performances resolve. A rerun SHALL add only what is missing and leave existing Albums and Tracks unchanged. A
non-Administrator gets 403, an unauthenticated request 401, and a request while an import runs 409. `GET
/api/albums/import-dump` SHALL return the state (`idle`, `running`, `done`, `failed`), the counts of Albums and Tracks
created and rows skipped, and the reason when failed.

#### Scenario: Import a recording whose Tunes are all known
- **GIVEN** the dump lists Dervish's "At the End of the Day" with a track of tunes 27 and 28, both in the catalogue
- **WHEN** an Administrator starts the import and it finishes
- **THEN** the Album exists by Dervish, an unclassified Artist, with one Catalogue Track whose two Performances are resolved and whose Set holds those Tunes

#### Scenario: An unknown Tune leaves the Track without a Set
- **GIVEN** a track lists tune 27 and a tune 999999 that is not in the catalogue
- **WHEN** the import finishes
- **THEN** the Catalogue Track has two Performances, the second unresolved with its listed name, and no Set

#### Scenario: Run again
- **GIVEN** an import has finished
- **WHEN** an Administrator starts it again
- **THEN** no Album or Track is created twice

#### Scenario: Not an Administrator
- **WHEN** alice, who is not an Administrator, sends `POST /api/albums/import-dump`
- **THEN** the response status is 403 Forbidden

#### Scenario: Download fails
- **GIVEN** the dump cannot be downloaded
- **WHEN** an Administrator starts the import
- **THEN** the state becomes `failed` with a reason and the Albums already created are kept

### Requirement: A Performance may be unresolved
The system SHALL allow a Performance with no Tune, carrying only the listed tune name, its position and no Transcription.
An unresolved Performance SHALL be exposed with its `listedName` and no `tune`, and SHALL NOT appear in a Tune's
Performances.

#### Scenario: Unresolved Performance in a Track
- **GIVEN** a Catalogue Track with an unresolved Performance listed as "The Silver Spear"
- **WHEN** a client requests `GET /api/tracks/{id}`
- **THEN** that Performance has `listedName` "The Silver Spear" and no `tune`

### Requirement: Attach uploaded audio to a Catalogue Track
The system SHALL let an authenticated Account attach an audio file to a Catalogue Track with `POST
/api/tracks/{id}/audio`, which stores the file under the same format and size rules as an upload, makes the Account its
Owner and turns it into an ordinary Track keeping its Album and Performances. Attaching to a Track that already has audio
SHALL answer 409 Conflict. An upload MAY name a Catalogue Track in `catalogueTrackId` to attach directly instead of creating a
new Track.

#### Scenario: Attach audio
- **GIVEN** a Catalogue Track "Cooley's"
- **WHEN** alice sends `POST /api/tracks/{id}/audio` with an mp3
- **THEN** the response status is 200 OK, the Track is no longer `catalogue`, plays, and alice is its Owner

#### Scenario: Already has audio
- **WHEN** alice attaches audio to a Track that has audio
- **THEN** the response status is 409 Conflict

#### Scenario: Choose a Catalogue Track at upload
- **WHEN** alice uploads an mp3 with `catalogueTrackId` of a Catalogue Track
- **THEN** no new Track is created and the Catalogue Track has the audio

### Requirement: Suggest a Catalogue Track for uploaded audio
The system SHALL let a client read `GET /api/tracks/{id}/catalogue-suggestions`, listing Catalogue Tracks of the same Artist
whose title matches the Track's ignoring case, punctuation and surrounding whitespace. The UI SHALL show them after an
upload, with an accept action that attaches the uploaded audio to the chosen one; ignoring the suggestion keeps the upload
as a separate Track.

#### Scenario: A match is suggested
- **GIVEN** a Catalogue Track "Cooley's" by Dervish and alice uploads a Track "cooley's" by Dervish
- **WHEN** a client requests its suggestions
- **THEN** the Catalogue Track is listed

#### Scenario: Accept in the UI
- **GIVEN** the upload page shows the suggestion
- **WHEN** alice accepts it
- **THEN** the audio is attached to the Catalogue Track and the separate uploaded Track is gone

### Requirement: Albums pages
The UI SHALL provide an Albums page listing Albums and an Album view listing its Tracks in order, each marked "No audio yet"
when it is a Catalogue Track and linking to its Tunes. Both SHALL be reachable by keyboard and update on Live updates.

#### Scenario: Browse an Album
- **WHEN** the Album view of "At the End of the Day" is opened
- **THEN** its Tracks are listed in order, those without audio marked "No audio yet"

## MODIFIED Requirements

### Requirement: A track may have no Set
The system SHALL treat a track without a Set like any other track: it is listed, fetched, edited, played (when it has audio) and deleted as usual, its `set` is absent and its `performances` is empty, except that a Catalogue Track whose entries are not all resolved keeps its Performances. It MAY be given a Set by Identification, once, after which it is a track with a Set. The UI SHALL show a track without a Set with no Tunes section, and a track that is given a Set shows it and its Performances once it is fetched again, without a reload of the page.

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

#### Scenario: A Set arrives while the details are open
- **GIVEN** the tracks page shows the open details of a track without a Set
- **WHEN** Identification gives the track the Set of "Cooley's" then "The Kesh"
- **THEN** the details show a Tunes section with those two Tunes and their start offsets, without a reload

#### Scenario: A Set is given once
- **GIVEN** a track that Identification gave the Set of "Cooley's"
- **WHEN** a later analysis recognises "The Kesh"
- **THEN** the track keeps the Set of "Cooley's" and its Performances
