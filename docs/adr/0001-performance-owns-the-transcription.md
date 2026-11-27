---
status: proposed
---

# A Tune is fixed, a Performance owns the Transcription, and a Set is unique per ordered list of Tunes

A Track is a recording of a Set of Tunes, and each recording of a Tune differs in key and melodic variation. So a Tune holds only what never changes (name, aliases, author, Dance type, usual key as a hint), and each **Performance** (one Tune as played in one Track) holds its own key, variation notes and ABC **Transcription**. This departs from thesession.org, where ABC belongs to a Setting of the Tune; we drop Settings. A **Set** exists independently of any Track, with one Set per distinct ordered list of Tunes, so a planned Set with no recording can later gain a Track.

## Considered options

- **Settings as on thesession.org**: ABC and key live on a Tune's Settings, and a Track points to one. Rejected because a recording rarely matches a written Setting exactly, and we want to keep each musician's interpretation.
- **Per-Track copy of all Tune data**: rejected because name and author fixes would not apply everywhere. Only the data that really varies is copied into the Performance.

## Consequences

- Tune identity comes from the thesession.org id; irishtune.info only adds references and aliases.
- Identification (FolkFriend) proposes Performances and Transcriptions, and manual corrections always win. Whether FolkFriend copes with full band recordings, and the licence and ABC coverage of the thesession.org data dump, are unverified. Transcription must stay editable by hand so the model does not depend on them.
- Existing `Track` (bare artist name, no Tunes) and `playlist` code do not yet match this model.
