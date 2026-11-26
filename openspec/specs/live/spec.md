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
instance of the application. The library event types are `track.indexed`, `track.unindexed` and `waveform.computed`,
each with the track's `id`.

#### Scenario: Anonymous and authenticated clients both receive a library event
- **GIVEN** one anonymous client and one client logged in as alice are connected
- **WHEN** track 42 becomes searchable
- **THEN** both receive `track.indexed` with id `42`

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
