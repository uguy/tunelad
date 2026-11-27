Issue: #821

# Proposal

## Why

Glossary: only the **Owner** or an **Administrator** may change a Track and its **Performances**. The code already enforces it (`PUT /api/tracks/{id}` carries the Performances, `POST /api/tracks/{id}/identification` checks the same rule) and a test covers a non-owner being refused. But the `track` spec only speaks of editing a track's metadata, and no test shows an Administrator editing someone else's Performances. The rule is true but not pinned down.

## What Changes

- Make the `track` spec say that the rule covers a Track's Performances (key, notes, Transcription, offset) as well as its metadata, with scenarios for the Owner, an Administrator and another Account.
- Add the missing test: an Administrator edits the Performances of a Track owned by someone else, and the Owner stays unchanged.
- No production behaviour change, no breaking REST API change, no OpenAPI change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `track`: the requirement "Only the owner or an administrator changes a track" names Performances explicitly and gains Performance scenarios.

## Impact

- Modulith modules: `track` (tests and spec only). `account` is only read, through `AccountRef.admin()`.
- REST API: unchanged. UI: unchanged.
- PR title: `test(track): cover administrator edits of performances`

## Out of scope

- New roles.
- Any change to who may run Identification, comment or delete.
- Production code changes, unless the new test reveals a gap.
