## Context

`tune` already imports one Tune by id through `TuneSource` (`ThesessionTuneSource`, JSON API) and stores it with
`Tunes.insert`, whose unique `source_id` index plus `ON CONFLICT DO NOTHING` makes inserts idempotent. The Administrator
role is read from the session through `account`'s current-account API.

## Goals / Non-Goals

**Goals:** run-time download, fully reactive streaming, idempotent and resumable, Administrator only, visible credit.
**Non-Goals:** other settings, popularity, scheduling, refreshing existing Tunes.

## Decisions

- **Live columns** (checked): `tunes.csv` is `tune_id,setting_id,name,type,meter,mode,abc,date,username,composer`, quoted fields hold
  line breaks, and settings of one tune are not contiguous, so the first row seen per `tune_id` is kept with a set of seen ids;
  `aliases.csv` is `tune_id,alias,name`. Because quoted fields span lines, a small incremental RFC 4180 reader is used.

- **Separate source from the per-id one.** A new `internal` `TuneDump` interface (with a `WebClient` implementation
  reading `https://raw.githubusercontent.com/adactio/TheSession-data/main/csv/`, base URL configurable for tests)
  streams CSV lines as `DataBuffer`s, decoded and split into rows without loading the file in memory. No blocking call.
- **First setting per Tune.** `tunes.csv` has one row per setting; the first row seen for a `tune_id` wins, later rows
  of that id are skipped. Row to `SourceTune` reuses the `ThesessionTuneSource.abc(...)` helpers (made package-visible
  for reuse) so Reference transcriptions match the single-Tune import. Composer goes to `author`.
- **Aliases.** `aliases.csv` is read first into a `Map<Integer, List<String>>` (small, a few tens of thousands of rows),
  then merged while streaming Tunes.
- **Idempotent and resumable by construction.** Rows are inserted one by one, 8 at a time, with
  `INSERT ... ON CONFLICT (source_id) DO NOTHING`, so a re-run after a crash only adds what is missing. No checkpoint
  is stored. Tunes already present are never modified (assumption: no overwrite of curated data).
- **Unknown Dance type or empty name** rows are skipped and counted, never failing the run.
- **Trigger.** `POST /api/tunes/import-dump` (Administrator, else 403; 401 unauthenticated) starts the job in the
  background and answers 202 Accepted; if one is running it answers 409 Conflict. `GET /api/tunes/import-dump` returns
  the state of the last run (`idle`, `running`, `done`, `failed`) with counts (`created`, `skipped`, `total`) and, on
  failure, the message. State is held in memory in one `AtomicReference` (single instance app, lost on restart, which
  is fine since a re-run resumes). OpenAPI gains both operations with these responses.
- **Credit.** A footer line "Data from thesession.org" linking to thesession.org in the app layout, using theme
  tokens, readable by everyone. Link text and destination follow the licence answer.
- **Module rules.** All code in `tune`; the Administrator check goes through `account`'s public API. No event is needed.

## Risks / Trade-offs

- Licence unconfirmed → merge blocked until answered.
- In-memory state is lost on restart → acceptable, a rerun is cheap and safe.
- GitHub raw availability → a failed download ends the run as `failed`, partial inserts stay, rerun resumes.
