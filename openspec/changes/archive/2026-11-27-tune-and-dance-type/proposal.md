Issue: #817

## Why

Tune Lad's domain model (see `GLOSSARY.md`) starts from the **Tune**: the piece that never changes between recordings.
Sets, Performances and Identification all refer to Tunes, so Tunes and their **Dance type** must exist first. This is
the first slice of the roadmap (#815).

## What Changes

- New `tune` Modulith module owning Tunes, their Dance type and their Reference transcription.
- An Account can create a Tune by hand: name, aliases, author, Dance type, optional usual key, optional Reference transcription (ABC).
- An Account can import a Tune from thesession.org by its id: name, aliases, author, Dance type, key and the Reference transcription are
  read from thesession.org. Importing an id already imported answers with the existing Tune instead of creating a second one.
- Browse Tunes: list (optionally filtered by Dance type and name/alias text) and a detail view.
- Dance type is a fixed vocabulary: jig, reel, hornpipe, slide, polka, slip jig, waltz, barndance, strathspey, march, mazurka, three-two.
- UI: a Tunes list page, a Tune detail page and a create/import form. The detail page switches between **Sheet music** (default when a Reference
  transcription exists) and **ABC text**, and a **Play** control plays the Reference transcription as synthesized MIDI audio (stop and tempo control).
- REST: new endpoints under `/api/tunes`, described in OpenAPI. No existing endpoint changes (nothing breaking).

## Capabilities

### New Capabilities
- `tune`: create, import from thesession.org, list and view Tunes with their Dance type and Reference transcription.

### Modified Capabilities
<!-- none -->

## Impact

- Backend: new package `org.tunelad.tune` (`package-info.java` allowing `infrastructure` and `account`), a `tune.sql` Liquibase changelog
  included in `db.changelog-master.yaml`, an outbound reactive HTTP client for thesession.org.
- Frontend: new routes under `app/src/routes/tunes`, a header link, Playwright and vitest tests, and the `abcjs` dependency (renderer and synth),
  lazy-loaded only when a Tune with a transcription is opened, with a small self-hosted soundfont.
- Docs: `GLOSSARY.md` (Tune identity wording, see design), `README.adoc` features.
- Suggested PR title: `feat(tune): add tunes with dance types`

## Out of scope

- Sets, Performances, Transcription per Performance, Identification.
- Editing ABC, and highlighting the playing note in the sheet.
- Playing a Tune through the Track player or alongside a Track.
- Editing or deleting Tunes, and administrator rules on Tunes.
- Live updates for Tunes (roadmap slice #824).
- Search integration with the `search` module.
