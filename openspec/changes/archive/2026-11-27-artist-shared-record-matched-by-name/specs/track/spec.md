# Spec Delta

## ADDED Requirements

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
