# Design

## Context

`track_artists(id, name)` with a unique index on `lower(name)`; `DbArtists.findOrCreate` does insert-then-select (R2DBC `DatabaseClient`). `Artist` is a record `(id, name)` in `track/internal`. Tracks reference it by `artist_id`. `TunePerformanceDTO` already carries the artist name. The UI Tune page is `app/src/routes/tunes/[id]/+page.svelte`.

## Goals / Non-Goals

**Goals:** classification, band membership, "Recorded by", all additive to the API.
**Non-Goals:** see proposal's Out of scope.

## Decisions

- **Schema**: `ALTER TABLE track_artists ADD COLUMN kind TEXT CHECK (kind IN ('musician','band'))` (null = unclassified) and `track_band_members(band_id, musician_id, PRIMARY KEY(band_id, musician_id), both FK to track_artists ON DELETE CASCADE)`. One changeset with rollback in `track.sql`. Existing rows stay null.
- **Domain**: `Artist` gains `ArtistKind kind` (enum, nullable); `Artists` gains `find`, `setKind`, `members`, `bands`, `replaceMembers`. Rules (a Band is not a member, members become Musicians, a Band with members is not demoted) live in a small `ArtistService`-style class in `track/internal`, not in SQL.
- **Replacing members** runs in one `TransactionalOperator` transaction: resolve names with `findOrCreate`, validate kinds, delete old links, insert new ones, set kinds. Reactive end to end, no blocking calls.
- **REST**: new `ArtistController` in `track/web`: `GET /api/artists/{id}`, `PUT /api/artists/{id}`, `PUT /api/artists/{id}/members`. Write endpoints require authentication like other track writes; any Account may call them (no owner concept for the shared Artist). Errors: 404, 409, 400 via the existing `ResponseStatusException` style. OpenAPI: new `Artists` tag, `ArtistDTO` schema, and `artistId`/`artistKind` added to `TunePerformance`.
- **Recorded by**: derived in the UI from `GET /api/tunes/{id}/performances` (distinct by `artistId`), then `GET /api/artists/{id}` per distinct Artist for members. No new tune endpoint, so `tune` gets no dependency on `track`.
- **Live**: Artist edits publish a `TrackUpdated`-style event only if existing events can express it; otherwise left out (not required by the slice). The Tune page refetches on its existing live events.
- **UI**: a "Recorded by" section on the Tune page with accessible controls (labelled select for kind, a labelled members input), shown only to signed-in Accounts; theme tokens only; no new dependency.

## Risks / Trade-offs

- Any Account can reclassify any Artist: accepted by the issue; vandalism is possible and mitigated only by the 409 rules.
- N+1 `GET /api/artists/{id}` on the Tune page: the number of distinct Artists per Tune is small.
- A name collision between a person and a band is resolved by the shared name match (one Artist per name), so the first classification wins.
