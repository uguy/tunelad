# Spec Delta

## ADDED Requirements

### Requirement: Announce a computed waveform
When a track's waveform becomes `READY` or `FAILED`, the system SHALL raise the library event `waveform.computed` with
that track's id on the live stream. It SHALL NOT raise it while the waveform stays `PENDING` (for example while
ffmpeg is missing).

#### Scenario: Waveform ready
- **GIVEN** a client is connected to `GET /api/live`
- **WHEN** the waveform of track 42 becomes `READY`
- **THEN** the client receives `waveform.computed` with id `42`

#### Scenario: Waveform failed
- **GIVEN** a client is connected to `GET /api/live`
- **WHEN** the audio of track 42 cannot be decoded and its waveform becomes `FAILED`
- **THEN** the client receives `waveform.computed` with id `42`

#### Scenario: Waveform stays pending
- **GIVEN** a client is connected to `GET /api/live` and ffmpeg is not installed
- **WHEN** the waveform computation of track 42 is attempted
- **THEN** the client receives no `waveform.computed` for track 42

### Requirement: Show a waveform as soon as it is computed
The UI SHALL replace a track's waveform placeholder with its waveform as soon as the system announces it computed,
without a reload and however long after the upload that happens. The UI SHALL NOT poll for waveforms.

#### Scenario: Waveform appears after an upload
- **GIVEN** alice is on the tracks page
- **WHEN** she uploads a track and its waveform is computed a few seconds later
- **THEN** that track's row shows its waveform within 2 seconds of the computation
- **AND** the page was not reloaded

#### Scenario: Waveform computed long after the list was shown
- **GIVEN** the tracks page shows track 42 with a placeholder because its waveform is `PENDING`
- **WHEN** the waveform is computed 5 minutes later, for example by the startup backfill after ffmpeg was installed
- **THEN** the placeholder is replaced by the waveform

#### Scenario: Waveform for a track that is not on screen
- **GIVEN** the tracks page is open and track 42 is far below the visible rows
- **WHEN** the waveform of track 42 is computed
- **THEN** the UI does not fetch it before the row nears the viewport
