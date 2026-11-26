# Proposal

## Why

The UI only learns about changes by reloading or polling. The tracks list polls each pending waveform every 3 s and
gives up after 10 attempts, so a waveform that finishes later (ffmpeg installed afterwards, backfill) never appears
without a reload. The list itself, served by the search read model, shows nothing of what other users upload, edit or
delete. Playlists and favourites are planned, and they need per-account updates (also across a user's own tabs and
devices).

The app must also scale horizontally. Events are handled by the instance that published them, so a browser connected
to instance A cannot learn what happened on instance B without a channel between instances. This change adds that
channel (NATS) and a server-to-browser stream (SSE).

## What Changes

- New Spring Modulith module `live`: bridges application events to NATS subjects, receives them on every instance,
  routes them in memory to the connected browsers, and serves them as Server-Sent Events on `GET /api/live`.
- Events carry identifiers only (`{type, id}`); the browser refetches through the normal, authorized API.
- Two audiences, with the audience decided by the server and never by the client:
  - **library** events (`tunelad.library.track.<trackId>.<kind>`): every connection, including anonymous ones, as the
    library itself is public;
  - **account** events (`tunelad.account.<accountId>.<topic>`): only connections of that account. The routing is built
    and tested now; favourites and playlists will publish to it later.
- `media` publishes a new `WaveformComputed` event once a waveform is saved; `search` publishes a new `TrackIndexed`
  event once a track's search copy is written (and `TrackUnindexed` once it is removed), so the UI refetches only
  when the data it reads is ready.
- UI: one `EventSource` opened from the layout; the tracks list refreshes when the library changes and a track's
  waveform appears as soon as it is computed. The polling in `TrackList.svelte` is removed.
- NATS (core, no JetStream) is added to `docker-compose.yaml`, to the application configuration and to the tests
  (Testcontainers).
- No breaking change to existing REST endpoints. One endpoint is added (`GET /api/live`).

## Capabilities

### New Capabilities
- `live`: the live update stream (`GET /api/live`), its event types and audiences, its behaviour when connections or
  instances come and go, and how the UI reacts to it.

### Modified Capabilities
- `media`: a track's waveform appears in the UI as soon as it is computed, without a reload and without a polling
  limit; the existing "Compute a track's waveform after upload" requirement is unchanged.
- `search`: a track that becomes searchable, or stops being searchable, is reflected in the tracks list shown in the
  UI without a reload.
- `track`: the tracks list reflects uploads, edits and deletions made by other users, without a reload.

## Impact

- **Modules**: new `live` (allowed dependencies: `search::event`, `media::event`, `account`; it
  listens to the events of the modules that own the state, not to `track` events, see design.md); `media` gains a
  `media::event` named interface; `search` gains a `search::event` named interface. `ModularApplicationTests` must
  keep passing.
- **REST API / OpenAPI**: adds `GET /api/live` (`text/event-stream`), documented in the OpenAPI description. It is
  permitted to anonymous clients like the other read endpoints, and needs no CSRF token (GET).
- **UI**: new `app/src/lib/live.svelte.ts` store, changes in `app/src/routes/+layout.svelte`,
  `app/src/routes/tracks/component/TrackList.svelte` (polling removed) and the tracks page.
- **Dependencies and infrastructure**: `io.nats:jnats`; a `nats` service in `docker-compose.yaml`; a NATS container
  in the test setup; connection properties in `application.yml`.
- **Operations**: NATS is a new service to run. Delivery is at-most-once and ephemeral by design; a client that
  reconnects refetches everything.

## Out of scope

- JetStream, durable or replayed delivery, and moving module-to-module events onto NATS: module events stay in
  process, on the Modulith Event Publication Registry.
- Multi-instance hardening of that registry (scheduled resubmission, startup backfill lock): a separate change.
- Playlists and favourites themselves, and their events; shared (multi-member) playlists.
- NATS authentication, TLS and clustering beyond what local development and tests need.
- Presence, typing indicators or any browser-to-server messaging; WebSocket.
- Pushing data in events (payloads stay ids only).

## Commit

PR title: `feat(live): push live ui updates through nats and server-sent events`
