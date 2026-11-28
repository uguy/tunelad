## Context

`track` already owns Artists, Tracks, Performances and may depend on `tune` and `set`. `tune` has the bulk dump import
(`TuneDumpImport`, `GithubTuneDump`, `CsvParser`, all internal). `recordings.csv` has one row per tune on a recording:
id, artist, recording (album title), track (number), number (position in track), tune, tune_id.

## Decisions

- **Album and import live in `track`**, since they need Artists and Performances. The import cannot reuse tune-internal
  classes; `CsvParser` and a generic dump downloader move to `infrastructure` (public) rather than `track` reaching into
  `tune.internal`. Tunes are looked up by thesession id through a new read method on `TuneCatalog` (`findBySourceId`), never
  created: absent means unresolved.
- **Catalogue Track = Track with an explicit `catalogue` flag and no audio** (`format` is already null for old uploads, so it cannot tell them apart). One table, no new aggregate: `track_tracks` gains `album_id` and
  `album_position`; audio columns/media reference stay nullable. Existing free-text `album` column is kept for uploads and
  linked by title when it matches an Album of the Artist (not migrated automatically).
- **Unresolved Performance**: `track_performances.tune_id` becomes nullable with `listed_name`. A Track's `set_id` is set
  (via `SetCatalog.findOrCreate`) only when all entries have a `tune_id`. Resolving later (Tune imported) is out of scope.
- **Idempotent import**: Albums matched by Artist + title ignoring case; a Catalogue Track by Album + position; rerun adds
  only the missing ones and never touches edited ones. Artists via existing `Artists` match-by-name, left unclassified.
  Runs in the background with a state/counters record like `TuneDumpImport`; database writes through the reactive
  transactional operator in batches per Album. No blocking calls.
- **Attach**: `POST /api/tracks/{catalogueTrackId}/audio` (multipart, Owner rules: any authenticated Account, who becomes
  Owner) stores the file through `media` exactly as upload does, then publishes `TrackUpdated`. Upload with
  `catalogueTrackId` form field does the same atomically. Suggestion: `GET /api/tracks/{id}/catalogue-suggestions`
  returns Catalogue Tracks with the same Artist and normalized-title match; the UI offers accept after upload.
- **Events**: `AlbumChanged` in `track::event` (a `@NamedInterface`); `live` forwards it as a Live update; `search` indexes
  Albums' title and Artist.
- **OpenAPI**: new Albums tag with CRUD (create by hand, get, list, update cover/date), `POST/GET
  /api/albums/import-dump` (Administrator, 202/409/403/401 like the Tune import), attach and suggestions operations; Track
  schema gains `catalogue` (boolean) and `albumId`/`albumPosition`.
- **Frontend**: `albums.svelte.ts` store with `refreshOnLive`; theme tokens only; Catalogue Tracks render without a player
  and with the text "No audio yet" (not colour alone); the suggestion banner is an `aria-live` region.

## Risks / Trade-offs

- ~30k recordings means a long import; mitigated by resumability and per-Album transactions.
- Title matching can suggest wrongly; it is only a suggestion, accept is explicit.
- Nullable `tune_id` loosens an invariant; guarded in the service and covered by tests.

## Open Questions

- Does `TuneCatalog` need a bulk lookup by thesession ids for speed (preload a map once per run)? Assumed yes.

## Implementation notes

- A Catalogue Track publishes `CatalogueTrackAdded` (not `NewTrackSaved`, whose listeners assume audio) and `AlbumChanged`; search indexes Catalogue Tracks from the former. The import therefore emits events per Track; batching them is a possible follow-up.
- The bulk Tune lookup is `TuneCatalog.findBySourceIds`; `CsvParser` and the dump downloader (`CsvDump`) moved to `infrastructure`.
- Albums are searchable through `GET /api/albums/search?q=` (`search_albums`); `search_tracks` carries `catalogue`, `album_id`, `album_position`.
