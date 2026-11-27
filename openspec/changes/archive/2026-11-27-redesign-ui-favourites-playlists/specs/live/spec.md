## ADDED Requirements

### Requirement: Favourite and playlist events reach only the owner's account
The system SHALL announce a change to a user's favourites with the event `favourite.changed` (its `id` is the track
id) and a change to one of their playlists, including its creation, deletion, renaming, tracks or order, with
`playlist.changed` (its `id` is the playlist id). These events SHALL reach every connection of the owning account on
every running instance, and no other connection, anonymous or authenticated. A track deletion that removes the track
from favourites or playlists SHALL raise the matching event for each account affected.

#### Scenario: Favourite marked in one tab
- **GIVEN** alice has the application open in two tabs
- **WHEN** alice marks track 42 as a favourite in the first tab
- **THEN** both tabs receive `favourite.changed` with id `42`

#### Scenario: Playlist changed
- **GIVEN** alice has the application open in two tabs
- **WHEN** alice adds a track to her playlist 7
- **THEN** both tabs receive `playlist.changed` with id `7`

#### Scenario: Other accounts and anonymous clients do not receive them
- **GIVEN** alice, bob and an anonymous visitor are connected
- **WHEN** alice marks a track as a favourite
- **THEN** alice receives `favourite.changed`
- **AND** bob and the anonymous visitor do not

#### Scenario: Track deleted
- **GIVEN** alice's playlist 7 and bob's favourites both hold track 42
- **WHEN** track 42 is deleted
- **THEN** alice receives `playlist.changed` with id `7` and bob receives `favourite.changed` with id `42`

#### Scenario: No content in the event
- **WHEN** alice's tab receives `playlist.changed`
- **THEN** the event data holds only its `type` and `id`, never the playlist's name or tracks

### Requirement: The UI follows favourites and playlists live
The UI SHALL refetch what it shows of the user's favourites and playlists when it receives `favourite.changed` or
`playlist.changed`, and after its live connection reopens: the favourite toggles, the favourites page, the playlists
list, an open playlist page and the counts on the account page. A tab that made the change SHALL end up showing the
same state as one that only heard about it.

#### Scenario: Favourite added elsewhere
- **GIVEN** the favourites page is open in one tab
- **WHEN** the user marks "Knees Up" as a favourite in another tab
- **THEN** the first tab lists "Knees Up" without a reload

#### Scenario: Playlist reordered elsewhere
- **GIVEN** a playlist page is open in one tab
- **WHEN** the user reorders its tracks in another tab
- **THEN** the first tab shows the new order without a reload

#### Scenario: Playlist deleted elsewhere
- **GIVEN** a playlist page is open in one tab
- **WHEN** the user deletes that playlist in another tab
- **THEN** the first tab says the playlist was not found

#### Scenario: Reconnect refreshes
- **GIVEN** the playlists page is open and its live connection drops
- **AND** the user creates a playlist in another tab while it is down
- **WHEN** the connection reopens
- **THEN** the list includes the new playlist without a reload

### Requirement: Live connections and events are measured
The system SHALL expose how many live connections are open and how many live events were published and delivered,
by event type, as metrics exported with the other application metrics.

#### Scenario: Connections counted
- **GIVEN** two clients are connected to `GET /api/live`
- **WHEN** the metrics are read
- **THEN** the open live connections gauge is 2

#### Scenario: Events counted by type
- **WHEN** alice marks a track as a favourite
- **THEN** the published events counter for `favourite.changed` increases by one
