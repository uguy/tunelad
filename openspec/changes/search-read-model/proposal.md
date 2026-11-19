# Proposal

## Why

The `search` module exists only as a `package-info.java`. Track search runs inside the `track`
module (`GET /api/tracks/search`), using a text index on the write model's own `tracks` collection.
This change makes search a real read-side module, following CQRS: search keeps its own projection
and fills it from the events that `track` publishes. It also applies the tool the repo is here to
learn: Spring Modulith's transactional, persisted event delivery (`@ApplicationModuleListener` with
the Event Publication Registry), which the app cannot use today.

Along the way, the delete-all endpoint (`DELETE /api/tracks`) goes away. It exists only so tests can
reset state between runs; no client uses it. Keeping it would mean the new read model has to handle
it too: a second event type, ordering problems between "delete everything" and "add track", and a
race in how the event is published today.

## What Changes

- **BREAKING**: `DELETE /api/tracks` is removed from the REST API, along with the delete-all
  command and the `AllTracksDeleted` event in `track`. API tests reset the database directly instead
  of going through HTTP.
- The `search` module owns a read model: a dedicated MongoDB collection with its own text index,
  kept up to date by listening to `NewTrackSaved` (add or replace the track).
- Events are delivered with `@ApplicationModuleListener`, backed by the Event Publication Registry
  (`spring-modulith-events-mongodb`), so delivery is at-least-once and incomplete publications can be
  resubmitted. This needs a reactive MongoDB transaction manager, which the app does not define
  today, so the `@Transactional` on the track service starts to take effect.
- A spike comes first. It checks that a transactional module listener fires for an event published
  within a reactive transaction, and whether the Mongo registry blocks the request path (and if so,
  how to isolate it). If the spike fails, events are delivered with a plain `@EventListener` instead,
  and `design.md` records why.
- `GET /api/tracks/search` moves to a controller in the `search` module and reads from the read
  model. The path, query parameter and JSON fields stay the same. The response is described by a
  search-owned DTO with the same fields as the track list.
- The text index comes off the track write model: `@TextIndexed` is removed from `Track`, and the
  search query is removed from `TrackService` and `Tracks`.
- Search becomes eventually consistent: a newly uploaded track becomes searchable shortly after the
  upload completes, rather than immediately.
- The `search` module's allowed dependencies shrink to `track` (for `TrackDTO` in event payloads)
  and `track::event`. `track::command` is dropped.
- No backfill. Tracks that are already stored are not copied into the read model, so local data is
  wiped when this lands (which also drops the stale text index on `tracks`).

Affected Modulith modules: `search` (new read model, search endpoint) and `track` (search and
delete-all removed, events published within transactions). REST API: **BREAKING**, because
`DELETE /api/tracks` is removed. The search endpoint changes implementation, but its path,
parameters and response fields stay the same; the only behaviour change there is that results are
eventually consistent. UI: no change. No UI code calls the delete endpoint, and
`app/src/routes/tracks/Tracks.ts` keeps calling the same search endpoint.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `search`: adds a requirement that an uploaded track becomes searchable shortly after its upload
  completes (eventual consistency). Existing matching, ordering and blank-query requirements are
  unchanged.
- `track`: removes the "Delete all tracks" requirement (`DELETE /api/tracks`).

## Impact

- **Code**:
  - New in `org.tunelad.search`: read-model document, repository, event listener and controller.
  - `TrackController`, `TrackService`, `DefaultTrackService`, `Tracks`, `DbTracks` and `Track` lose
    search and delete-all.
  - `DeleteAllTrackCommand`, `AllTracksDeleted` and their handlers are deleted.
  - A new configuration bean defines a `ReactiveMongoTransactionManager`.
- **Dependencies**: adds `spring-modulith-events-mongodb` (or the matching Modulith starter).
  The tests may need `spring-modulith-starter-test` for the `Scenario` API.
- **Infrastructure**: MongoDB transactions require a replica set. Testcontainers' `MongoDBContainer`
  already runs one. The local Mongo setup must switch to a single-node replica set.
- **Data**: a new search collection plus the registry's event publication collection. Existing
  local data must be wiped.
- **Tests**:
  - `AbstractApiTest` resets the database directly instead of calling `DELETE /api/tracks`.
  - The "Clear a populated library" test is removed.
  - `TrackSearchApiTest` must wait for the projection after uploading, using Awaitility or the
    Modulith `Scenario` API.
  - `ModularApplicationTests` must still pass with the reduced dependencies.
- **OpenAPI**:
  - The `DELETE /api/tracks` operation disappears.
  - The `/api/tracks/search` operation moves to a search controller and tag, with the same path,
    parameter and response schema fields.

## Out of scope

- Moving play / audio streaming out of `track` (a separate, later change).
- Serving the track list or track-by-id from a read model (full CQRS read side).
- Backfilling or rebuilding the read model from existing tracks (no startup rebuild, no reindex
  endpoint).
- Replacing delete-all with any other delete feature, such as deleting a single track or an admin
  endpoint.
- Events or search updates for editing or deleting a single track (the app has neither feature).
- Changing search matching, ranking or the endpoint URL.

## PR title

`refactor(search)!: serve search from a read model and drop the delete-all endpoint`
