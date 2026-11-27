Issue: #818

## Why

A **Set** is what a Track will record (roadmap #815): an ordered list of Tunes, shared by all Accounts, with or without recordings.
Tunes exist since #817; Sets come next so Tracks and the Learning list can point at them later.

## What Changes

- New `set` Modulith module owning Sets and their ordered Tunes.
- An authenticated Account creates a Set from an ordered list of Tune ids. **One Set exists per distinct ordered list of Tunes**: posting the same
  list again, by anyone, answers with the existing Set instead of creating another (also when two requests race).
- Browse Sets: list and detail view showing the Tunes in order (name, Dance type), each linking to its Tune page.
- The `tune` module publishes a small API (`TuneCatalog`) so `set` can check that Tunes exist and read their names without touching `tune.internal`.
- UI: a Sets list, a Set page, and a builder to pick Tunes (search), add, reorder and remove them before creating the Set.
- REST: new endpoints under `/api/sets`, described in OpenAPI. Additions only, nothing breaking.

## Capabilities

### New Capabilities
- `set`: create (get-or-create), list and view Sets of Tunes.

### Modified Capabilities
<!-- none: the tune spec is unchanged, TuneCatalog is an internal module API -->

## Impact

- Backend: new package `org.tunelad.set` (`package-info.java` allowing `infrastructure`, `account`, `tune`), `set.sql` changelog, `TuneCatalog` in
  `org.tunelad.tune` (a named API), the table truncate in `AbstractApiTest`.
- Frontend: routes `app/src/routes/sets`, header link, Playwright and vitest tests.
- Docs: `GLOSSARY.md` (checked, see tasks), `README.adoc` features.
- Suggested PR title: `feat(set): add sets of tunes`

## Out of scope

- Tracks (a Track pointing at a Set), Performances.
- Learning list.
- Editing or deleting a Set (a Set is defined by its Tunes: another list is another Set).
- Deleting Tunes, and Live updates for Sets (roadmap #824).
