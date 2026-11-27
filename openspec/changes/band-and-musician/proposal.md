Issue: #845

# Proposal

## Why

An **Artist** is only a name today. The glossary models it as a **Musician**, a **Band** or not yet classified, with Bands made of Musicians, so that a Tune can show who recorded it and the repertoire slices that follow (albums, sets, advice) can talk about people and bands.

## What Changes

- An **Artist** gets a classification: **Musician**, **Band** or unclassified. Every existing Artist stays unclassified.
- Any **Account** can classify an Artist and, for a **Band**, edit its members (**Musicians**), through the REST API and the UI. A Musician not yet known is created by name on first use, like any Artist.
- The Tune detail view shows **Recorded by**: the distinct Artists of the Tune's **Performances**, as Musicians and Bands (a Band lists its members).
- `TunePerformance` gains the Artist's `artistId` and `artistKind`; the existing `artist` name field is unchanged (not breaking).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `track`: Artists are classified as Musician or Band, Bands have Musicians as members, editable by any Account; Performances of a Tune expose their Artist.
- `tune`: the Tune detail view shows "Recorded by".

## Impact

- Modules: `track` (Artist, REST API, `TunePerformanceDTO`); `live` only if an Artist change publishes an event (see design). `tune` is served by the track module's controller and by the UI only.
- REST API: new `/api/artists` endpoints, additive field on `TunePerformance`. Not breaking.
- UI: Tune detail ("Recorded by"), a way to classify an Artist and edit Band members.
- Database: Liquibase changeset in `track.sql` (kind column, `track_band_members` table).
- `GLOSSARY.md`: Artist/Band/Musician already describe the target; update only if wording changes during implementation.

## Out of scope

- Albums and catalogue tracks (#847), Repertoire, search by Artist kind.
- Merging or deleting Artists; renaming an Artist.
- Artist pages listing all their Tracks.
- Changing the upload form: the `artist` text part still creates unclassified Artists.
- Role/permissions beyond "any Account".

## Suggested PR title

`feat(track): classify artists as musicians and bands`
