# Tasks

## 1. Branch

- [x] 1.1 Update `main` (`git switch main && git pull`) and create `feat/track-ownership-control` from it. Verify `git status` shows the new branch with a clean tree, apart from this change's `openspec/changes/track-ownership-control/` files (commit those first as `docs(openspec): propose track-ownership-control change`)

## 2. Administrator role

Commit: `feat(account): recognise administrators from the identity provider's admin role`

- [x] 2.1 In `docker/keycloak/tunelad-realm.json`, add the realm role `admin`, a `oidc-usermodel-realm-role-mapper` on the `tunelad` client writing realm roles to a multivalued `roles` claim (access token, ID token, userinfo), and a user `admin` (password `admin`, name "Tune Lad Admin") with the `admin` role. Verify, after `docker compose up -d --force-recreate keycloak`, that a password-grant access token for `admin` has `"roles"` containing `admin` and alice's doesn't
- [x] 2.2 Map the `roles` claim to `ROLE_` authorities for bearer tokens: set `spring.security.oauth2.resourceserver.jwt.authorities-claim-name: roles` and `authority-prefix: ROLE_` in `application.yml` (fall back to a `ReactiveJwtAuthenticationConverter` bean in `SecurityConfig` if the reactive auto-configuration ignores them, see design.md decision 1)
- [x] 2.3 Add a `GrantedAuthoritiesMapper` bean in `SecurityConfig` that keeps the existing authorities and adds `ROLE_<role>` for each `roles` entry of an `OidcUserAuthority`, with a unit test covering: roles present, no `roles` claim, and non-OIDC authorities passed through
- [x] 2.4 Add `admin` to `AccountRef`, set it in `DefaultCurrentAccount` from the `ROLE_admin` authority, and document the field (required boolean) in the `GET /api/me` OpenAPI schema. Fix existing `AccountRef` constructor uses in main and test code. Verify `/v3/api-docs` shows `admin`
- [x] 2.5 Add a `TestTokens.admin()` helper (a token whose `roles` claim contains `admin`), and tests for the account spec: in `AccountApiTest` "Authenticated" (body has `admin`), "Token with the admin role" and "Token without the admin role"; in `BrowserSessionSecurityTest` "Browser login with the admin role" (stored session whose OIDC user has `roles: [admin]`, with authorities from the application's `GrantedAuthoritiesMapper` bean); in `KeycloakIntegrationTest` "Local realm administrator". Verify they pass
- [x] 2.6 In README.adoc's "Developing" section, add the realm user `admin`/`admin` as the app administrator (not Keycloak's master-realm admin), and the `--force-recreate keycloak` step to pick up realm changes. Verify the documented commands run as written

## 3. Edit and delete tracks through the API

Commit: `feat(track): let owners and admins edit and delete tracks through the api`

- [x] 3.1 Add `infrastructure.ForbiddenException` and map it to 403 in `ExceptionTranslator`
- [x] 3.2 Add `MediaStore.delete(String key)` and implement it in `GridFsMediaStore` (GridFS delete by filename), with a `GridFsMediaStoreTest` case checking that the audio can't be found after deletion and that deleting a missing key completes without error
- [x] 3.3 Add `UpdateTrackCommand` and `DeleteTrackCommand` (carrying `AccountRef by`) to `track::command`, the events `TrackUpdated(TrackDTO)` and `TrackDeleted(String trackId)` to `track::event`, `Track.isChangeableBy(AccountRef)`, and `TrackService` methods implementing design.md decisions 2 and 4: 404 before 403, metadata replaced with owner/format kept, document then audio deleted, events published inside the transaction. Add a `delete` to `Tracks`/`DbTracks`. Verify `ModularApplicationTests` passes
- [x] 3.4 Add `PUT /api/tracks/{id}` (validated `TrackEdit` JSON body, returns the updated `TrackDTO`) and `DELETE /api/tracks/{id}` (204) to `TrackController`, with the OpenAPI operations, `bearerAuth` requirement, `TrackEdit` schema and responses listed in design.md decision 3. Require authentication for `PUT /api/tracks/*` and `DELETE /api/tracks/*` in `SecurityConfig`. Verify `/v3/api-docs` shows both operations and their responses
- [x] 3.5 Add `AbstractApiTest` helpers `edit(id, body, token)` and `delete(id, token)`, then add `TrackApiTest` tests, one per scenario, for "Edit a track's metadata", "Delete a track", "Only the owner or an administrator changes a track" and "Changing a track requires authentication". Check that `deleting_all_tracks_is_not_allowed` still passes. Verify `./mvnw verify -DskipITs` passes
- [x] 3.6 Extend `TrackEventsTest`: an edit publishes `TrackUpdated` once with the updated track, a deletion publishes `TrackDeleted` once with its id, and a refused (403) or unknown (404) change publishes nothing. Verify they pass

## 4. Search follows edits and deletions

Commit: `feat(search): reflect track edits and deletions in search results`

- [x] 4.1 Add `@ApplicationModuleListener` methods to `SearchProjection` for `TrackUpdated` (save, replacing by id) and `TrackDeleted` (`deleteById`), blocking on the listener thread like the existing one
- [x] 4.2 Add `SearchProjectionTest` cases: `TrackUpdated` replaces the copy, `TrackDeleted` removes it, and redelivering `TrackDeleted` for an already-removed track completes without error. Add `TrackSearchApiTest` tests for the spec's "Edited track found by its new title", "Edited track has the track list fields" and "Deleted track disappears from search" (polling up to 5 seconds, like the existing upload test). Verify they pass

## 5. Edit and delete from the UI

Commit: `feat(ui): edit and delete own tracks from the track list`

- [x] 5.1 Add `admin: boolean` to the `Account` type in `app/src/lib/store/me.ts`, and add `app/src/lib/permissions.ts` with `canChange(me, track)` plus a vitest unit test (anonymous, owner, other user, admin, track without owner). Verify `npm run test:unit` passes
- [x] 5.2 Add a `request(fetch, method, path, data?)` helper to `app/src/lib/api.ts` (CSRF header on non-GET, returns the `Response`), and `update(id, edit)` / `delete(id)` to `Tracks.ts` that throw on a non-OK response
- [x] 5.3 In `TrackList.svelte`, render "Edit" and "Delete" buttons (with `aria-label`s that include the title) when `canChange($me, track)`. Edit shows an inline form with Save/Cancel, Delete asks with `window.confirm`, and failures show a `role="alert"` message on the track. Dispatch `trackUpdated`/`trackDeleted` so `+page.svelte` replaces or removes the track in `data.tracks`
- [x] 5.4 Add `app/e2e/track-ownership.test.js` (API mocked with `page.route`) with one test per scenario of "Edit and delete own tracks from the UI", "Edit a track from the UI" and "Delete a track from the UI", asserting the PUT/DELETE method, URL, body and `X-XSRF-TOKEN` header where the spec says so. Add `admin: false` to the account fixtures in the existing e2e tests. Run `npm run format` and `npm run lint`, and verify `npm run test:e2e` passes

## 6. Finish

- [x] 6.1 In README.adoc's Features, add "Owners edit and delete their own tracks, and administrators (Keycloak `admin` role) any track, from the API or the track list". Add a curl example for `PUT`/`DELETE` with a bearer token next to the upload recipe, and verify it works against the running app
- [ ] 6.2 Run `./mvnw clean verify` and verify it passes, including `ModularApplicationTests`, the Keycloak test, vitest and the Playwright e2e tests
- [x] 6.3 Smoke-test with `docker compose up -d` and the built app: as alice, edit and delete one of her tracks from the track list and see search catch up within a few seconds. As bob, see no controls on alice's tracks, and get 403 from a curl `DELETE`. As `admin`, edit one of bob's tracks
- [ ] 6.4 Push `feat/track-ownership-control` and open a PR to `main` titled `feat(track): let owners and admins edit and delete tracks`, mentioning the Keycloak container recreate step in the description

## Workflow follow-up

- Archive the change with `/openspec-archive-change` on the feature branch before merging, so specs land in `main` with the code. When archiving, update the `track` spec's Purpose to mention editing and deleting tracks.
