Issue: #841

# Proposal

## Why

After slice 1 a Track can be uploaded from its audio file alone, but it then has no Set, no Performances and no
Identification: the app still asks the Account to say which Tunes are played before it will listen. The recognition engine
can find the Tunes in a recording without being told which, so the main flow (drop in a recording, let Tune Lad work out
the Tunes and the Set) is still missing. This is slice 3 of that flow, after file-only upload (#839) and WMA (#837).

## What Changes

- For a Track without a Set, after upload, Identification analyses the audio in an open-ended mode: the sidecar is asked
  for every Tune it recognises, not those of a given Set. The sidecar's `POST /identify` accepts a request without `tunes`
  and then reports any Tune of its index, with the same time, end and confidence fields.
- Detections are kept when their confidence is high enough (configurable, default 0.5). A kept Tune the catalogue lacks is
  imported from thesession.org by its id, owned by the Track's Owner, like `POST /api/tunes/import`; one that cannot be
  imported is left out. The kept Tunes, in the order first heard, give an
  ordered list; the Track gets the Set for that list (found or created, one Set per distinct ordered list) and one
  Performance per Tune, in order, each pending then identified at the start heard, exactly as restricted Identification
  proposes them.
- Nothing recognised, or no recognised Tune known to the application: the Track stays Set-less and is left as it was.
- The Set is attached once, only to a Track that still has none, under the Track's row lock, so an Account that chose or
  built a Set in the meantime wins. A Set of one Tune is valid.
- A Track that already has a Set is analysed as today, restricted to the Tunes of its Set.
- Attaching the Set announces the Track as updated, so search, the live updates and the UI show the Set and the
  Performances without a reload. The Track's details show them like any other Track's.
- The re-run action on a Set-less Track now runs the open-ended analysis instead of doing nothing.
- `GLOSSARY.md`: Identification can also derive the Set of a Track that has none.

No REST request or response shape changes: a Track's `set` and `performances` are already optional and empty for a
Track without a Set. The only observable difference is that they now fill in after upload.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `identification`: the open-ended analysis, the rules for keeping detections, deriving and attaching the Set, and the
  re-run on a Set-less Track.
- `track`: a Track without a Set can be given one once (system-only), creating its Performances; the UI shows a derived Set.
- `tune`: Identification imports the Tunes it recognises that the catalogue lacks, owned by the Track's Owner.

## Impact

- **Modulith modules**: `identification` (open-ended `IdentificationSource` method, the decision logic, a Set-less branch in
  `IdentificationService`; `allowedDependencies` gains `set`),
  `track` (a new command, handled under the row lock, that attaches a Set and its Performances to a Set-less Track and
  publishes `TrackUpdated`), `set` (`SetCatalog` gains a find-or-create for an Account), `tune` (`TuneCatalog` gains
  lookup by thesession.org id and import for an Account), `live` and `search` (existing events only).
- **Sidecar**: `sidecar/identification` accepts a request without `tunes`; its README, tests and limits are updated
  (the whole index is searched, which costs more time than the restricted mode).
- **REST API / OpenAPI**: none changes; the re-run endpoint's description says it also derives a Set. Not breaking.
- **UI**: the tracks list and details show the Set and Performances that arrive after upload; the Identification badge
  states follow. No new screen.
- **Database**: none expected; confirm in design.md that `track_tracks.set_id` and the Performances tables need no change.
- **Tests**: Testcontainers integration tests with a recording stub for the sidecar; sidecar tests for the open-ended mode;
  Playwright only if the UI changes beyond what slice 1 shows.
- **Proposed PR title**: `feat(identification): derive a track's set from its audio`.

## Out of scope

- Choosing or changing a Track's Set by hand after upload, and correcting a derived Set in the UI.
- Creating Tunes by hand, or importing anything but the Tunes recognised in the audio.
- Guessing the artist, album or title beyond slice 1's tags.
- Re-identifying existing Tracks in bulk.
- Changing how a Performance's start is placed or how confidence is computed in the engine.
