# Spec Delta

## MODIFIED Requirements

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
