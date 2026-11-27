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
- Identification proposes Performances and manual corrections always win. Spikes (`docs/research/identification-sources.md`): the bare FolkFriend CLI fails on a real duo recording, whereas Cadence's pipeline (note decoder, tuned query, Viterbi segmenter) identifies a tune of it reliably. Identification therefore runs Cadence's engine in a Node sidecar container behind a port; irishtune.info has no API or licence and is not used. The first slice proposes start offsets only; key and Transcription stay entered by hand until a later slice, so the model does not depend on the engine.
- The sidecar contains AGPL-3.0 and GPL-3.0 code. It is kept as a separate container with its source public in this repository and its own licence; the rest of the repository keeps its licence.
- Tunes absent from the thesession.org index cannot be identified.
- Existing `Track` (bare artist name, no Tunes) and `playlist` code do not yet match this model.
