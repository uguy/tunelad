Issue: #816

# Proposal

## Why

Today a Track's artist is a bare string copied onto every Track, so "Altan" and "altan" are unrelated and nothing
ties one Artist's Tracks together. GLOSSARY.md defines an **Artist** as a shared record, created on first use and
matched by name. This is the first slice of the domain model roadmap (#815) and the other slices build on it.

## What Changes

- Add an **Artist** record, shared by all Tracks, matched by name (trimmed, case-insensitive).
- Uploading or editing a Track with an artist name reuses the matching Artist, or creates it on first use.
- A Track still returns its Artist as the `artist` name string, now the Artist's stored name, so two spellings of
  one name show the same way in lists and search.
- A blank artist means the Track has no Artist.
- Existing Tracks are migrated: each distinct artist name becomes one Artist.
- No breaking REST API change: `artist` stays a string in requests and responses, so the OpenAPI description is
  unchanged. No UI change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `track`: a Track's artist is a shared Artist record matched by name, created on first use. The `search`
  capability is unchanged: its copy of a Track already takes `artist` from the Track.

## Impact

- Modulith modules: `track` (new `track_artists` table, Artist lookup on upload and edit), `search` (verified by a
  test only; its read model keeps the artist name from track events). REST API: behaviour only, same shape. UI: none.
- Database: new Liquibase changeset in `track.sql` that creates the table and migrates `track_tracks.artist_name`.
- Proposed PR title: `feat(track): add shared artists`.

## Out of scope

- Artist pages, and listing or searching Tracks by Artist.
- Merging Artists, renaming an Artist, or deleting orphan Artists.
- Exposing an Artist id in the API.
- Other roadmap slices (Tune, Set, Performance).
