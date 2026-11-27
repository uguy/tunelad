Issue: #846

## Why

The Tune catalogue only grows one Tune at a time, by hand or by thesession.org id. Identification and Sets need a
catalogue that already knows the traditional repertoire. thesession.org's open data dump (adactio/TheSession-data) lists
every Tune with its aliases and composer, so an Administrator can fill the catalogue in one run.

## What Changes

- An Administrator can start a bulk import that downloads `tunes.csv` and `aliases.csv` from adactio/TheSession-data at
  run time (nothing is stored in git) and creates the missing Tunes.
- Per Tune the import keeps the **first setting only** (name, Dance type, usual key, Reference transcription), plus the
  composer as the Tune's author and the aliases. No popularity.
- The import is idempotent (a Tune already known by its thesession.org id is left as it is) and resumable (a run
  interrupted part way is finished by running it again).
- The Administrator can read the progress and the outcome of the last run.
- The UI shows a "Data from thesession.org" credit.
- Corrects the tune spec: thesession.org's dump does have a composer, so imported Tunes may carry an author.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `tune`: adds the bulk import and the credit; the single-Tune import requirement no longer says thesession.org
  publishes no author.

## Impact

- Modules: `tune` (REST API and UI both). Needs `account` only for the Administrator check (already allowed).
- REST API: new endpoints to start and read the bulk import, Administrator only; no breaking change. OpenAPI description
  gains them.
- UI: an Administrator trigger with progress, and a footer credit visible to everyone.
- Database: no new column; a small table or in-memory state for the last run (see design).
- Suggested PR title: `feat(tune): bulk import thesession.org tunes, aliases and composers`.

## Risks and open questions

- **Blocker: terms of reuse.** The dump declares no licence. Confirm thesession.org's terms of reuse (and the
  attribution they expect) before merging; the credit text and link may change with the answer.
- The exact CSV column names (composer in particular) are to be confirmed against the live files at the start of the
  implementation.

## Out of scope

- Importing every setting of a Tune, popularity or recording counts.
- Albums and catalogue Tracks from recordings (issue #847).
- Scheduled or automatic imports; only an Administrator starts one.
- Updating Tunes already in the catalogue from newer dumps.
