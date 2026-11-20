# Proposal

## Why

Every track now has an owner (`add-user-accounts`), but a track can't be changed or removed once it's uploaded. A typo
in a title stays there for good, and an unwanted upload can only go by wiping the database. Owners need to fix and
remove their own tracks. Someone also needs to be able to clean up other people's uploads, so an administrator role
from the identity provider may do the same on any track.

## What Changes

- New `PUT /api/tracks/{id}`: replaces a track's metadata (title, description, artist, album) and returns the
  updated track. The audio, format and owner never change.
- New `DELETE /api/tracks/{id}`: removes a track together with its stored audio. Afterwards the track can't be
  fetched or played.
- Only the track's owner, or a user with the identity provider's `admin` role, may edit or delete it. Anyone else who
  is authenticated gets 403 Forbidden, anonymous clients get 401 Unauthorized, and an unknown id gets 404 Not Found.
  Browser sessions still need their CSRF token on these requests.
- An administrator is a user whose token or login carries the `admin` realm role. The local Keycloak realm defines
  that role, puts realm roles in a `roles` claim and gets a new user `admin`/`admin` who has it. `GET /api/me` gains
  a boolean `admin` field.
- Search reflects edits and deletions within the same 5 seconds it already promises for uploads: edited tracks are
  found by their new words, and deleted tracks no longer appear.
- UI: in the track list, the logged-in user's own tracks (every track, for an administrator) get **Edit** and
  **Delete** controls. Edit opens an inline form for the metadata, and Delete asks for confirmation first. The list
  updates as soon as the server answers, without waiting for search to catch up.
- No breaking REST API change: two endpoints are added, and `GET /api/me` gets one more field.

Affected Modulith modules: `track` (new endpoints, commands, events, authorization rule), `search` (read model
follows the new events), `account` (admin flag on the current account), plus `infrastructure` (security rules and
role mapping). `media` only gets a delete operation on its public `MediaStore` API. The change touches both the REST
API and the UI.

Proposed PR title: `feat(track): let owners and admins edit and delete tracks`

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `track`: adds requirements for editing and deleting a track, the owner-or-admin rule, and the Edit/Delete controls
  in the UI.
- `search`: adds a requirement that edits and deletions reach search results within 5 seconds.
- `account`: `GET /api/me` also returns `admin`, and a new requirement says where the administrator role comes from.

## Impact

- **REST API**: new `PUT /api/tracks/{id}` (JSON body) and `DELETE /api/tracks/{id}`, both documented in OpenAPI
  with the `bearerAuth` security requirement and their 200/204, 400, 401, 403 and 404 responses. `GET /api/me`
  response schema gains `admin`.
- **Backend**: `track` (commands, service, controller, two new events in `track::event`), `search` projection,
  `account` current-account resolution, `media` `MediaStore.delete`, and `SecurityConfig` (rules for PUT/DELETE,
  mapping the `roles` claim to authorities for both bearer tokens and browser logins).
- **Keycloak realm** (`docker/keycloak/tunelad-realm.json`): `admin` realm role, realm-roles mapper to a `roles`
  claim, user `admin`. Run `docker compose down -v` (or re-import the realm) to pick it up locally.
- **UI**: `TrackList.svelte`, `Tracks.ts`, and the `me` store type (`admin`).
- **Tests**: API tests (Testcontainers), search projection tests, a Keycloak integration check for the role, and
  Playwright e2e tests for the controls.
- **Docs**: README features list.

## Out of scope

- Replacing a track's audio file (delete and upload again instead)
- Transferring ownership, or changing the owner's display name on stored tracks
- Optimistic concurrency for edits (ETag / `If-Match`, 409 Conflict). Two concurrent edits are not handled
  specially.
- Partial updates (`PATCH`): an edit replaces all four metadata fields
- Soft delete, undo, or an audit trail of who edited or deleted what
- Other roles or fine-grained permissions, and managing roles from the app (roles are managed in the identity
  provider; the Google profile never yields an administrator)
- An upload form in the UI, a "My tracks" page, or filtering by owner
- Private tracks
