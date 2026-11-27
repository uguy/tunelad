# live Specification

## Purpose
Tell the browser, as it happens, that something it displays has changed, so the UI refreshes without a reload or
polling. Updates reach every running instance of the application, and each one only reaches the people allowed to
see it.

## Requirements

### Requirement: Live update stream
The system SHALL serve `GET /api/live` as a stream of Server-Sent Events (`text/event-stream`) that stays open until
the client disconnects, to anonymous and authenticated clients alike.

#### Scenario: Anonymous client opens the stream
- **GIVEN** nobody is logged in
- **WHEN** a client requests `GET /api/live`
- **THEN** the response is 200 with content type `text/event-stream`
- **AND** the response stays open

#### Scenario: Authenticated client opens the stream
- **GIVEN** alice is logged in
- **WHEN** her browser requests `GET /api/live`
- **THEN** the response is 200 with content type `text/event-stream`

### Requirement: Events carry identifiers, not data
Each event on the stream SHALL have an event name equal to its type and a JSON `data` object with the `type` and, when
the event concerns one entity, its `id`. Events SHALL NOT carry the entity's content, so that what a client learns
about an entity always goes through the API and its authorization.

#### Scenario: Shape of an event
- **GIVEN** a client is connected to `GET /api/live`
- **WHEN** the waveform of track 42 is computed
- **THEN** the client receives an event named `waveform.computed` whose data is `{"type":"waveform.computed","id":"42"}`

### Requirement: Library events reach every connection
Events about the shared track library SHALL reach every connection, anonymous or authenticated, on every running
instance of the application. The library event types are `track.indexed`, `track.unindexed`, `waveform.computed` and
`audio.prepared`, each with the track's `id`.

#### Scenario: Anonymous and authenticated clients both receive a library event
- **GIVEN** one anonymous client and one client logged in as alice are connected
- **WHEN** track 42 becomes searchable
- **THEN** both receive `track.indexed` with id `42`

#### Scenario: Anonymous and authenticated clients both receive an audio event
- **GIVEN** one anonymous client and one client logged in as alice are connected
- **WHEN** the audio of track 42 becomes ready to play
- **THEN** both receive `audio.prepared` with id `42`

### Requirement: Account events reach only that account
Events addressed to an account SHALL reach every connection of that account (for example several tabs or devices), and
no connection of any other account or any anonymous connection.

#### Scenario: Two tabs of the same account
- **GIVEN** alice has the application open in two browser tabs
- **WHEN** an event addressed to alice's account is raised
- **THEN** both tabs receive it

#### Scenario: Other accounts and anonymous clients do not receive it
- **GIVEN** alice, bob and an anonymous visitor are connected
- **WHEN** an event addressed to alice's account is raised
- **THEN** alice receives it
- **AND** bob and the anonymous visitor do not

### Requirement: The client cannot choose its audience
The system SHALL decide which events a connection receives from the identity of the request alone. Query parameters,
headers or any other client-supplied value SHALL NOT widen it.

#### Scenario: Asking for another account's events
- **GIVEN** bob is connected
- **WHEN** a client logged in as alice requests `GET /api/live?account=<bob's id>`
- **THEN** the client receives library events and alice's own account events only

### Requirement: Events cross application instances
An event raised on one instance of the application SHALL be delivered to the matching connections on every other
instance.

#### Scenario: Waveform computed on another instance
- **GIVEN** two instances of the application share one database
- **AND** a client is connected to the first instance
- **WHEN** the second instance computes the waveform of track 42
- **THEN** the client receives `waveform.computed` with id `42`

### Requirement: Keep idle connections open
While a connection is open the system SHALL send a comment line at least every 30 seconds, so that proxies and load
balancers do not close an idle connection.

#### Scenario: Heartbeat on a quiet stream
- **GIVEN** a client is connected and no event is raised
- **WHEN** 30 seconds pass
- **THEN** the client has received at least one heartbeat comment

### Requirement: A slow connection does not hold up the others
A client that reads slowly or not at all SHALL NOT delay the delivery of events to other connections. Events not
delivered to such a client MAY be dropped.

#### Scenario: One stalled client among several
- **GIVEN** one client has stopped reading its stream and another is connected
- **WHEN** a library event is raised
- **THEN** the other client receives it without waiting for the stalled one

### Requirement: Live updates are best effort and never block the application
The system SHALL treat live delivery as best effort: events MAY be lost, and a failure of the messaging infrastructure
between instances SHALL NOT make any other operation fail or slow down. Delivery SHALL resume without a restart once
that infrastructure is available again.

#### Scenario: Messaging infrastructure is down during an upload
- **GIVEN** the messaging infrastructure between instances is unavailable
- **WHEN** alice uploads a track
- **THEN** the upload succeeds with 201 Created
- **AND** the track becomes searchable as usual

#### Scenario: Delivery resumes
- **GIVEN** the messaging infrastructure was unavailable and is available again
- **WHEN** the waveform of a track is computed
- **THEN** connected clients receive `waveform.computed` for it

### Requirement: The UI keeps one live connection while it is open
The UI SHALL open the live stream when the application loads and keep a single connection per browser tab. When the
logged-in account changes (login or logout) it SHALL reopen the stream so that it matches the new identity.

#### Scenario: One connection per tab
- **WHEN** a user navigates between pages of the application in one tab
- **THEN** the tab holds a single open connection to `GET /api/live`

#### Scenario: Login reopens the stream
- **GIVEN** an anonymous visitor has the application open
- **WHEN** they log in as alice
- **THEN** the stream is reopened and receives alice's account events from then on

### Requirement: The UI recovers after a lost connection
When the UI's live connection is lost and reopens, the UI SHALL refresh what it displays that depends on live events
(the tracks list and the waveforms shown), because events raised in the meantime were not delivered.

#### Scenario: Reconnect refreshes the list
- **GIVEN** the tracks page is open and its live connection drops
- **AND** bob uploads a track while it is down
- **WHEN** the connection reopens
- **THEN** the tracks list includes bob's track once it is searchable, without a reload

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

### Requirement: Comment events reach every connection
The system SHALL announce a change to the Comments of a Track, a Tune or a Set (posted, edited, deleted, including
deletion with the Track) with the event `comment.changed`, whose `id` is the id of the subject. It SHALL reach every
connection, anonymous or authenticated, on every running instance, because Comments are public. The event SHALL NOT
carry the Comment's text.

#### Scenario: Comment posted
- **GIVEN** an anonymous client and a client logged in as bob are connected
- **WHEN** alice posts a Comment on Tune 12
- **THEN** both receive `comment.changed` with id `12`

#### Scenario: Track deleted
- **GIVEN** Track 42 has Comments and a client is connected
- **WHEN** Track 42 is deleted
- **THEN** the client receives `comment.changed` with id `42`

### Requirement: The UI follows comments live
The UI SHALL refetch the Comments it shows when it receives `comment.changed` for the displayed subject, and after its
live connection reopens.

#### Scenario: Comment posted elsewhere
- **GIVEN** a Track page is open in one browser
- **WHEN** another Account comments on that Track
- **THEN** the first page lists the new Comment without a reload
