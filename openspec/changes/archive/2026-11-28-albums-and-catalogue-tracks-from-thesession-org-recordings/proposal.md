Issue: #847

## Why

The collection only holds recordings someone uploaded. thesession.org lists tens of thousands of recordings (Albums with
their Tracks and the Tunes on them). Importing them gives every Tune a discography to browse and gives uploaded audio a
place to land, without waiting for Identification.

## What Changes

- New **Album**: a release by one Artist with a title, an optional cover and an optional release date; created by hand or
  imported from thesession.org `recordings.csv`.
- New **Catalogue Track**: a Track with no audio yet, with a place (position) on an Album. A Track's audio file stops being
  required; a Catalogue Track has no stream and is not playable.
- **Performance** may be unresolved: it keeps only the listed tune name when thesession.org does not identify the Tune. A
  Track gets a Set only when every one of its entries resolves to a Tune; otherwise it has Performances but no Set.
- Administrator-started import of `recordings.csv` (`POST /api/albums/import-dump`, progress with `GET`), mirroring the
  bulk Tune import. Imported Artists are **unclassified**; Tunes missing from the catalogue are not created (the entry stays
  unresolved), so the Tune import should run first.
- Uploaded audio and Catalogue Tracks: on upload with an Artist, a matching Catalogue Track (same Artist, similar title) is
  **suggested**; accepting attaches the audio to it. At upload an Account may also pick a Catalogue Track directly, which
  attaches without a suggestion.
- UI: Albums page and Album view listing Catalogue Tracks (marked "no audio yet"), an attach choice in the upload form, a
  suggestion banner after upload, create-by-hand Album form.
- `GLOSSARY.md`: Track no longer requires audio; Performance and Set wording for unresolved entries.

No breaking REST changes: audio becomes optional only on the new Catalogue Track paths; `POST /api/tracks` still requires a file.

Modules: `track` (Album, Catalogue Track, unresolved Performance, attach, import), `tune` (read access to resolve names,
if not already public), `live` (Album events), `search` (Albums and Catalogue Tracks in results). REST and UI both.

## Capabilities

### New Capabilities

None; Albums belong to the `track` module.

### Modified Capabilities

- `track`: Albums, Catalogue Tracks, unresolved Performances, recordings import, attaching audio.
- `tune`: Tune view lists the Albums it appears on (via Catalogue Tracks and Tracks).

## Impact

- Liquibase: `track_albums`, nullable audio and unresolved `track_performances.tune_id` + `listed_name`.
- `track` module package-info allowed dependencies; new `@NamedInterface` for album events; `live` listens to them.
- OpenAPI: new `/api/albums/**`, attach endpoint, `Track` schema gains `album`/`catalogue` flags.
- Frontend `app/`: albums routes, upload form, store `albums.svelte.ts`.
- External: raw.githubusercontent.com adactio/TheSession-data `recordings.csv`.

## Out of scope

- Fetching cover images from thesession.org; covers are set by hand only.
- Creating Tunes during the recordings import (the bulk Tune import does that).
- Classifying imported Artists (Musician/Band) automatically.
- Fuzzy audio fingerprint matching; suggestion is by Artist and title only.
- Playlists or Favourites of Catalogue Tracks.

Suggested PR title: `feat(track): import albums and catalogue tracks from thesession.org recordings`
