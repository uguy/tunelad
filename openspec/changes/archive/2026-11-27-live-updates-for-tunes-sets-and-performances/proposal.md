Issue: #824

## Why

Tunes, Sets and Performances are now created and edited by several Accounts, but an open browser only learns about
Tracks, Favourites, Playlists and Comments. A **Live update** (GLOSSARY) for the new entities lets a Tune, Set or
Performance list or page refresh when someone else changes what it displays, without a reload.

## What Changes

- A Tune created or imported announces `tune.changed` (id of the Tune); a bulk import of thesession.org tunes announces one `tune.changed` without id when it ends.
- A Set created announces `set.changed` (id of the Set).
- A Track's Performances changing (Track added, edited, Performances proposed by identification, Set attached, Track deleted) announces `performance.changed` (id of the Track).
- All three are public like the library events: they reach every connection, on every instance, and carry no content.
- UI: the Tunes list, the Tune page (including its Performances), the Sets list and the Set page refetch on these events and after the live connection reopens.
- REST API: none changed. `GET /api/live` gains three event types, documented in its OpenAPI description. Nothing breaking.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `live`: the `tune.changed`, `set.changed` and `performance.changed` events, and the UI following them.

## Impact

- Modulith modules: `live` (bridge), `tune` and `set` (new `event` named interfaces), `track` (already exposes `track::event`). `live` gains `tune::event`, `set::event` and `track::event` in `allowedDependencies`. Touches the REST API description and the UI.
- No database change.
- GLOSSARY.md: `Live update` lists Tunes, Sets and Performances.
- Suggested PR title: `feat(live): publish events for the new entities`.

## Out of scope

- A new transport: events keep using NATS and Server-Sent Events.
- Carrying entity content in events.
- Editing or deleting Tunes and Sets (no such paths exist; their events come with those slices).
- Live updates for Bands, Musicians, Albums, Repertoire (later slices announce their own).
