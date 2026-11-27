# Design

## Context

`Track` embeds an `Artist(name)` record into the `artist_name` column of `track_tracks`; `TrackDTO.artist` is a
string; `search` copies that string from `NewTrackSaved` and `TrackUpdated`. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:** one Artist row per matched name; no change to the REST shape, the events or the UI.

**Non-Goals:** Artist ids in the API, Artist events, renaming or merging (see proposal Out of scope).

## Decisions

- **Table `track_artists` in the `track` module** (`id` TEXT, `name` TEXT), with a unique index on `lower(name)`.
  `track_tracks` gets a nullable `artist_id` referencing it (same module prefix, so a foreign key is allowed). The
  old `artist_name` column is dropped in the same changeset, after backfill. Alternative: a separate `artist`
  module, rejected as nothing else needs Artists yet; it can be extracted later.
- **Matching is `lower(trim(name))`**, enforced by the unique index, not by application code alone. Alternative:
  exact match, rejected because "altan" and "Altan" would split the record. Accents and punctuation are not
  normalised.
- **Get-or-create by `INSERT ... ON CONFLICT (lower(name)) DO NOTHING`, then `SELECT`**, inside the track
  transaction, so two concurrent first uploads of one name end up on one row. Done with `DatabaseClient` or a repository
  query, fully reactive; no blocking call.
- **`Track` holds `artistId`; `TrackMapper` resolves the name** by joining on read. `track.internal` gets an
  `Artists` port (like `Tracks`) with a `db` implementation. `TrackDTO` is unchanged, so events and `search` need
  no code change. `Track` keeps a transient `artistName` filled by the join, so `DefaultTrackService` only calls
  `artists.findOrCreate(name)` on upload and edit.
- **Blank or null name means no Artist** (`artist_id` null), as today's behaviour for a missing artist.
- **OpenAPI**: unchanged.

## Risks / Trade-offs

- [Unique index fails on migration if data has names equal ignoring case] → the migration inserts
  `SELECT DISTINCT ON (lower(trim(artist_name)))` first, so it cannot collide.
- [Artists are never deleted, so renamed or cleared ones can be left with no Track] → accepted; orphan cleanup is
  out of scope.
- [A Track's displayed artist can differ in case from what was typed] → intended, covered by specs.

## Migration Plan

One Liquibase changeset in `track.sql`: create `track_artists`, backfill from distinct trimmed names, add
`artist_id`, backfill it by joining on `lower(trim(artist_name))`, drop `artist_name`. Rollback: add `artist_name`
back and copy names from the join.
