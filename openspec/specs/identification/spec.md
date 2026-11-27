# identification Specification

## Purpose
Propose where each Tune of a Track's Set starts by analysing the uploaded audio in the background, without delaying the upload. Each Performance says whether it is pending, identified from the audio, nothing found, or corrected by an Account, and an Account's correction is never overwritten by a later run. The Owner of a Track or an Administrator can run the analysis again through the REST API; the UI shows each Performance's state and offers the re-run.

## Requirements

### Requirement: Identification runs after upload
After a Track is saved, the system SHALL start Identification of its audio in the background, without delaying the upload response. For a Track with a Set it looks for the Tunes of that Set and, until it finishes, every Performance of the Track SHALL have the state `pending`. For a Track without a Set it listens for any Tune and may give the Track a Set.

#### Scenario: Upload returns before analysis ends
- **GIVEN** a Set of "Cooley's" then "The Kesh" exists and the sources are slow
- **WHEN** an authenticated client uploads a track for it
- **THEN** the response status is 201 Created
- **AND** both Performances of the track have the state `pending`

#### Scenario: Upload without a Set returns before analysis ends
- **GIVEN** the analysis service is slow
- **WHEN** an authenticated client uploads a file with no `setId`
- **THEN** the response status is 201 Created
- **AND** the track has no Set until the analysis ends

### Requirement: Proposals fill Performances
When Identification finishes, the system SHALL, for each Performance whose Tune the analysis detected in the Track's audio, propose the detection's start as the Performance's start offset and set its state to `identified`. Only Tunes of the Track's Set SHALL be accepted: a detected Tune that is not in the Set is ignored.

#### Scenario: Proposal accepted
- **GIVEN** a track of the Set "Cooley's" then "The Kesh" whose Performances are `pending`, and the analysis detects "The Kesh" starting at 95.5 s
- **WHEN** Identification finishes
- **THEN** the second Performance has start offset 95.5 and the state `identified`

#### Scenario: Tune outside the Set
- **GIVEN** the analysis detects a Tune that is not in the Track's Set
- **WHEN** Identification finishes
- **THEN** no Performance takes its start offset

#### Scenario: A later run finds it
- **GIVEN** a Performance that is `nothing_found`
- **WHEN** Identification is run again and detects its Tune
- **THEN** the Performance takes the detection's start and is `identified`

### Requirement: Nothing found
When Identification finishes, a Performance that was `pending`, whose Tune the analysis looked for (it has a thesession.org id) and did not detect, SHALL keep its values and become `nothing_found`. A Performance that was `identified` or `corrected` SHALL be left as it is. A Performance whose Tune has no thesession.org id is never looked for and SHALL stay as it is; if no Tune of the Set has one, the analysis SHALL not be asked for at all.

#### Scenario: Nothing detected
- **GIVEN** a track whose Performances are `pending` and the analysis detects none of its Tunes
- **WHEN** Identification finishes
- **THEN** the Performances keep their values and are `nothing_found`

#### Scenario: Only some Tunes heard
- **GIVEN** a track of the Set "Cooley's" then "The Kesh" and the analysis detects only "The Kesh"
- **WHEN** Identification finishes
- **THEN** the first Performance is `nothing_found` and the second is `identified`

#### Scenario: A later run no longer hears an identified Tune
- **GIVEN** a Performance that is `identified`
- **WHEN** Identification is run again and does not detect its Tune
- **THEN** the Performance keeps its start offset and stays `identified`

#### Scenario: Tunes that cannot be recognised
- **GIVEN** a Set whose only Tune was made by hand, with no thesession.org id
- **WHEN** the track is uploaded
- **THEN** the analysis is not asked for anything
- **AND** the Performance stays `pending`

### Requirement: Manual corrections are never overwritten
A Performance in the state `corrected` SHALL keep its values through any Identification, including a re-run. A proposal SHALL replace only values of Performances that are `pending`, `nothing_found` or `identified`.

#### Scenario: Re-run keeps a correction
- **GIVEN** an Account edited the start offset of a Performance to 90, making it `corrected`
- **WHEN** Identification is re-run and detects that Tune starting at 95.5 s
- **THEN** the start offset is still 90 and the state is still `corrected`

### Requirement: Re-run Identification
The system SHALL let the Owner of a Track or an Administrator re-run Identification with `POST /api/tracks/{id}/identification`, responding 202 Accepted. For a Track without a Set the re-run is the open-ended analysis. Other Accounts get 403 Forbidden, an unknown Track 404 Not Found.

#### Scenario: Owner re-runs
- **WHEN** the Owner requests a re-run
- **THEN** the response status is 202 Accepted and Identification runs again

#### Scenario: Owner re-runs a track without a Set
- **GIVEN** a Track without a Set whose first analysis recognised nothing
- **WHEN** the Owner requests a re-run and the analysis now recognises "Cooley's"
- **THEN** the response status is 202 Accepted and the Track gets the Set of "Cooley's"

#### Scenario: Not the Owner
- **WHEN** an Account that is neither Owner nor Administrator requests a re-run
- **THEN** the response status is 403 Forbidden

### Requirement: The analysis service can fail
If the analysis service is unreachable, times out or answers with an error, Identification SHALL end without failing the Track, leave the Performances unchanged and allow a re-run.

#### Scenario: Service down
- **GIVEN** the analysis service is unreachable
- **WHEN** Identification runs
- **THEN** the Track and its Performances are unchanged, still `pending` (not `nothing_found`), and a re-run is possible

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

### Requirement: The Set of a Track without one is derived from its audio
For a Track without a Set, Identification SHALL ask the analysis service for every Tune it recognises, keep those it is confident about, and give the Track the Set of those Tunes in the order they were first heard, with one Performance per Tune in that order whose start is where the Tune was heard and whose state is `identified`.

#### Scenario: Two tunes are recognised
- **GIVEN** a Track without a Set whose audio plays "Cooley's" from 0 s and "The Kesh" from 95 s, and the Tunes are in the catalogue
- **WHEN** Identification runs
- **THEN** the Track has the Set of "Cooley's" then "The Kesh"
- **AND** its Performances are "Cooley's" starting at 0 s then "The Kesh" starting at 95 s, both `identified`
- **AND** a client connected to `GET /api/live` receives `track.indexed` with the Track's id

### Requirement: A derived Set is a Set like any other
A Set found or created by Identification SHALL follow the rules of every Set: one Set per distinct ordered list of Tunes, and a Set of one Tune is valid. The Track SHALL be announced as updated, so searches and connected browsers show the Set and its Performances without a reload.

#### Scenario: The Set already exists
- **GIVEN** the Set of "Cooley's" then "The Kesh" exists and a Track without a Set plays them in that order
- **WHEN** Identification runs
- **THEN** the Track has that existing Set and no second Set exists

#### Scenario: Another order is another Set
- **GIVEN** the Set of "Cooley's" then "The Kesh" exists and a Track without a Set plays "The Kesh" first
- **WHEN** Identification runs
- **THEN** the Track has a new Set of "The Kesh" then "Cooley's"

#### Scenario: One tune
- **GIVEN** a Track without a Set whose audio plays only "Cooley's"
- **WHEN** Identification runs
- **THEN** the Track has the Set of "Cooley's" alone, with one `identified` Performance

### Requirement: Which recognised Tunes are kept
Identification SHALL ignore a detection whose confidence is below the configured minimum (`tunelad.identification.min-confidence`, default 0.5), merge consecutive detections of the same Tune into one, keep a Tune heard again after another Tune as a further entry, and keep at most the first twenty entries, the limit of a Set.

#### Scenario: Weak detections are ignored
- **GIVEN** the analysis hears "Cooley's" with confidence 0.8 and "The Kesh" with confidence 0.2
- **WHEN** Identification runs on a Track without a Set
- **THEN** the Track's Set is the Set of "Cooley's" alone

#### Scenario: A split detection is one entry
- **GIVEN** the analysis hears "Cooley's" from 0 s, again from 20 s with no other Tune between, then "The Kesh" from 95 s
- **WHEN** Identification runs on a Track without a Set
- **THEN** the Track's Set is "Cooley's" then "The Kesh"

#### Scenario: A repeated tune
- **GIVEN** the analysis hears "Cooley's", then "The Kesh", then "Cooley's" again
- **WHEN** Identification runs on a Track without a Set
- **THEN** the Track's Set is "Cooley's", "The Kesh", "Cooley's", with three Performances

#### Scenario: More than twenty entries
- **GIVEN** the analysis hears twenty-five different Tunes
- **WHEN** Identification runs on a Track without a Set
- **THEN** the Track's Set has the first twenty, in the order heard

### Requirement: Nothing recognised leaves the Track as it is
When no detection is kept, or none of the kept Tunes can be found or imported, Identification SHALL leave the Track without a Set and unchanged, and an Account SHALL be able to run Identification again.

#### Scenario: Nothing heard
- **GIVEN** the analysis recognises no Tune
- **WHEN** Identification runs on a Track without a Set
- **THEN** the Track still has no Set, no Performances and its other fields as before

#### Scenario: Recognised but unusable
- **GIVEN** the analysis hears a Tune that thesession.org does not have
- **WHEN** Identification runs on a Track without a Set
- **THEN** the Track still has no Set

#### Scenario: One tune cannot be imported
- **GIVEN** the analysis hears "Cooley's" then a Tune thesession.org cannot be reached for, then "The Kesh"
- **WHEN** Identification runs on a Track without a Set
- **THEN** the Track's Set is "Cooley's" then "The Kesh"

### Requirement: A Set an Account chose is never replaced
Open-ended Identification SHALL give a Set only to a Track that still has none when it finishes. A Track that has a Set, from upload or given while the analysis ran, SHALL keep it and its Performances unchanged by the open-ended result, and is analysed only against the Tunes of its Set.

#### Scenario: A Track with a Set is analysed as before
- **GIVEN** a Track with the Set of "Cooley's" then "The Kesh"
- **WHEN** Identification runs
- **THEN** the analysis service is asked only for those two Tunes and the Track's Set is unchanged

#### Scenario: The Set arrives while the analysis runs
- **GIVEN** a Track without a Set is being analysed
- **WHEN** the Track is given a Set before the analysis ends
- **THEN** the result of the open-ended analysis is discarded and the Track keeps its Set and Performances

### Requirement: The analysis service can fail without a Set
If the analysis service is unreachable, times out or answers with an error for a Track without a Set, Identification SHALL end without failing the Track and leave it as it was, and allow a re-run.

#### Scenario: Service down
- **GIVEN** the analysis service is down
- **WHEN** a Track without a Set is uploaded
- **THEN** the upload succeeds, the Track has no Set, and a later re-run can still give it one
