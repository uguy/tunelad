# Design

## Context

See proposal.md for motivation. These are the parts of the current system that shape the approach:

- **Who is calling.** `account.CurrentAccount.get()` resolves the request's `Authentication` (an `OidcUser` session
  or a `JwtAuthenticationToken`) into `AccountRef(id, displayName)`. Today no authorities other than Spring's
  defaults (`OIDC_USER`, `SCOPE_*`) exist, and nothing maps the identity provider's roles.
- **Security rules** live in `infrastructure.web.security.SecurityConfig`: only `POST /api/tracks` and
  `GET /api/me` require authentication. CSRF is enforced on state-changing requests from logged-in browser sessions,
  so PUT and DELETE are already covered. `DELETE /api/tracks` (no id) answers 405, and a test keeps it that way.
- **Track writes.** `DefaultTrackService` is `@ReactiveTransactional`. It saves the `Track` document, stores audio
  through `media.MediaStore.store(key, audio)` with the track id as key, and publishes `NewTrackSaved(TrackDTO)`
  through `TransactionalEvents` inside the transaction. `Track` already has a `@Version` field.
- **Errors.** `infrastructure.NotFoundException` is mapped to 404 by `ExceptionTranslator`. The previous design set
  the goal of keeping `track` free of Spring Security types.
- **Playback ignores the track.** `MediaController.play` looks the audio up in GridFS by key and never checks the
  `tracks` collection. Audio that outlives its track stays playable.
- **Search** is a read model (`search_tracks`) kept up to date by `SearchProjection`, an
  `@ApplicationModuleListener` that runs after commit on a task executor thread and blocks there.
- **UI.** `TrackList.svelte` (Svelte 5 in legacy syntax: `export let`, `createEventDispatcher`) renders a
  `<details>` per track. The `me` store holds `GET /api/me`. `api.ts` `send()` adds the CSRF header but swallows
  errors (returns `[]`) and always parses JSON, so it can't report a 403 or handle a 204.
- **Keycloak realm** (`docker/keycloak/tunelad-realm.json`) has users `alice` and `bob`, no roles, and one client
  `tunelad` with an audience mapper. Keycloak runs `start-dev` with its own in-container database.

## Goals / Non-Goals

**Goals:**

- One authorization rule ("owner or admin") with one home, in the `track` module, applied the same way to edits and
  deletions whether the caller uses a session or a bearer token.
- Map the identity provider's roles to Spring Security authorities the idiomatic way, for both login paths, so that
  `account` only has to read authorities.
- Keep `track` free of Spring Security types and `search` free of `account` types, as before.
- Stay reactive end to end on request paths.

**Non-Goals:**

- Method security (`@PreAuthorize`) or a general permission framework. One rule doesn't need it.
- Strict ordering of read-model updates (see Risks).

## Decisions

### 1. The `admin` role becomes the `ROLE_admin` authority, for bearer tokens and browser logins

The Keycloak realm gets a realm role `admin` and a protocol mapper on the `tunelad` client
(`oidc-usermodel-realm-role-mapper`) that writes realm roles to a flat, multivalued `roles` claim in the access token,
the ID token and userinfo. A flat claim avoids parsing Keycloak's nested `realm_access.roles`, and any OIDC provider
can be set up to emit it.

- **Bearer tokens:** Spring Boot's resource-server properties
  `spring.security.oauth2.resourceserver.jwt.authorities-claim-name: roles` and `authority-prefix: ROLE_` make the
  auto-configured JWT converter produce `ROLE_admin`. If the reactive auto-configuration turns out not to honour
  them, the fallback is a `ReactiveJwtAuthenticationConverter` bean in `SecurityConfig` with the same settings.
  Either way, the default `SCOPE_*` authorities go away, and nothing uses them.
- **Browser logins:** a `GrantedAuthoritiesMapper` bean in `SecurityConfig` keeps the existing authorities and adds
  `ROLE_<role>` for each entry in the `roles` claim of an `OidcUserAuthority` (ID token, or userinfo).
  `oauth2Login()` picks the bean up automatically. The Google profile emits no `roles` claim, so Google users are
  never administrators.

`account` then derives the flag: `AccountRef` becomes `AccountRef(id, displayName, admin)`, and
`DefaultCurrentAccount` sets `admin` when the authentication holds `ROLE_admin`. The flag isn't stored on the
account document. It belongs to the current authentication, so a role change applies with the next token or login,
as the spec says. `GET /api/me` returns it as is.

- *Alternative: read the `roles` claim directly in `account` (via `Identity.claims`).* Less code, but authorities
  are how Spring Security models this, and `SecurityConfig` could not use `hasRole` later.
- *Alternative: store an `admin` flag on the account, managed in the app.* That needs admin management in the app,
  which is out of scope, and roles already live in the identity provider.

### 2. Commands carry the acting account; `track` enforces the rule in its service

New commands in `track::command`:

```
UpdateTrackCommand(String id, title, artist, album, description, AccountRef by)
DeleteTrackCommand(String id, AccountRef by)
```

`TrackService` gains `Mono<TrackDTO> execute(UpdateTrackCommand)` and `Mono<Void> execute(DeleteTrackCommand)`.
The service loads the track, answers `NotFoundException` when it's missing (404 is checked before 403, as the spec
requires), then calls `Track.isChangeableBy(AccountRef)`: owner id equals the account id, or the account is an admin.
If neither holds, the service fails with a new `infrastructure.ForbiddenException`, which `ExceptionTranslator` maps
to 403 next to `NotFoundException`.

Commands use `account.AccountRef` (`track` already depends on `account`). They never leave `track`, so `search`
still sees only `TrackDTO` and `track` events.

- *Alternative: `@PreAuthorize` with a bean that loads the track.* It would load the track twice and put Spring
  Security annotations in `track`. With a single rule, the check reads more clearly in the domain.
- *Alternative: throw Spring Security's `AccessDeniedException`.* `ExceptionTranslationWebFilter` would map it to
  403, but that brings Spring Security types into `track`, against the previous design's goal.

### 3. REST: `PUT /api/tracks/{id}` (JSON) and `DELETE /api/tracks/{id}`

- `PUT` consumes `application/json` with a new `TrackEdit(title, description, artist, album)` record
  (`@NotBlank` on title and description) validated with `@Valid`. WebFlux answers a violation with 400 (problem
  details are on). The controller resolves `CurrentAccount`, builds `UpdateTrackCommand` and returns the updated
  `TrackDTO` with 200. `PUT` fits because the body replaces the whole editable metadata, and omitted optional fields
  are cleared.
- `DELETE` returns `Mono<Void>` with `@ResponseStatus(NO_CONTENT)`.
- `SecurityConfig` adds `PUT /api/tracks/*` and `DELETE /api/tracks/*` as `authenticated()`. Anonymous callers get
  401 from the existing `/api/**` entry point before any lookup. `DELETE /api/tracks` without an id still has no
  handler and keeps answering 405.

**OpenAPI changes:** two new operations under the `Tracks` tag ("Edit a track", "Delete a track"), each with
`security = @SecurityRequirement(name = "bearerAuth")` and documented responses: PUT 200 (`TrackDTO`), 400, 401,
403 ("not the owner or an administrator, or a browser session request without a valid CSRF token"), 404; DELETE 204,
401, 403, 404. There's a new `TrackEdit` request schema. The `AccountRef` schema behind `GET /api/me` gains a
required boolean `admin`. Nothing is removed or renamed, so existing clients keep working.

### 4. Delete removes the audio synchronously, in the same flow

`MediaStore` (the public API of `media`) gains `Mono<Void> delete(String key)`, which `GridFsMediaStore` implements
with `ReactiveGridFsOperations.delete(filename = key)`. `DefaultTrackService` deletes the track document, then the
audio, then publishes `TrackDeleted`, all in the same reactive transaction.

The deletion has to be synchronous because playback looks only at GridFS: with an event-driven, asynchronous
deletion the spec's "deleted track can no longer be played" would be false for a moment. It also mirrors how upload
already calls `MediaStore.store` directly, and `media` keeps depending on nothing but `infrastructure`.

- *Alternative: `media` listens to `TrackDeleted`.* That would give `media` a dependency on `track::event`, and the
  audio would stay playable until the listener runs.

Whether GridFS operations join the Mongo transaction is to be checked during implementation (`store` is in the same
situation today). The ordering keeps the failure modes acceptable either way: if the audio deletion fails, the
transaction rolls back and the track stays with its audio.

### 5. Two new events, and the search projection follows them

`track::event` gains `TrackUpdated(TrackDTO track)` and `TrackDeleted(String trackId)`, published through
`TransactionalEvents` inside the transaction, like `NewTrackSaved`, so listeners only see committed changes. The
Event Publication Registry delivers them at least once.

`SearchProjection` gets two more `@ApplicationModuleListener` methods. `TrackUpdated` saves `SearchTrack.of(track)`,
which replaces the copy by id. `TrackDeleted` calls `deleteById`. Both are idempotent under redelivery. As with the
existing listener, each method calls `.block()` on purpose: module listeners run on a task executor thread, never on
an event loop thread, and the registry marks the publication complete when the method returns.

- *Alternative: one `TrackChanged` event with a kind field.* Separate event types say what happened, and match how
  Modulith listeners pick events by type.

### 6. UI: permission helper, inline edit form, native confirm, local list updates

- The `me` store's `Account` type gains `admin: boolean`.
- A small pure function in `app/src/lib/permissions.ts`, `canChange(me, track)`, returns
  `me != null && (me.admin || track.owner?.id === me.id)`. It is unit-tested with vitest. `TrackList.svelte` uses it
  to render the **Edit** and **Delete** buttons, labelled "Edit"/"Delete" with an `aria-label` that includes the
  title so tests and screen readers can tell tracks apart. Clicks call `preventDefault` so they don't toggle the
  `<details>`.
- **Edit** swaps the track's details for a form (title, description, artist, album) with **Save** and **Cancel**.
  **Delete** asks with `window.confirm` (Playwright handles it with `page.on('dialog')`). An inline confirm was
  considered, but the native dialog is simpler and accessible by default.
- `api.ts` gains a `request(fetch, method, path, data?)` helper that adds the CSRF header like `send()` but returns
  the `Response`. `Tracks.ts` gains `update(id, edit)` (returns the updated track) and `delete(id)`, and both throw
  on a non-OK response. `send()` stays as it is, so the existing callers keep their behaviour.
- After a successful save or deletion, the list is updated locally from the response (replace the track or drop it)
  through `trackUpdated` and `trackDeleted` events up to `+page.svelte`. Re-running the search would show stale
  data, because search is eventually consistent. A failure shows an inline `role="alert"` message on that track and
  leaves it unchanged.

## Risks / Trade-offs

- [Read-model updates can apply out of order: module listeners run concurrently, so a `NewTrackSaved` or
  `TrackUpdated` handled after a later `TrackUpdated`/`TrackDeleted` for the same track leaves stale data, or brings a
  deleted track back into search] → It takes two changes to the same track within milliseconds, which a person in the
  UI won't produce. Accepted for now. If it shows up, carry the track `@Version` in the events and make projection
  writes conditional on a newer version.
- [Concurrent edits: the second save of a stale `Track` raises an optimistic-locking error, surfacing as 500] →
  Rare for a solo playground. Proper 409/ETag handling is out of scope (proposal).
- [Replacing the JWT authorities converter drops `SCOPE_*` authorities] → Nothing uses them. If a rule ever needs
  scopes, combine both converters.
- [A browser session keeps the roles it had at login] → Stated in the spec. Logging out and in again picks up a
  change.
- [A GridFS delete that isn't transactional can, if the commit itself fails afterwards, leave a track without audio]
  → Only on a commit failure after a successful delete. Playback then answers 404, and deleting the track again
  cleans up.
- [A realm user named `admin` is easy to confuse with Keycloak's bootstrap `admin` (master realm)] → They live in
  different realms. The README says which one logs in to the app.

## Migration Plan

1. Keycloak imports the realm only when it doesn't exist yet. Recreate the container to pick up the new role, mapper
   and user: `docker compose up -d --force-recreate keycloak` (its data lives in the container). Existing sessions
   and tokens carry no `roles` claim until the next login or token.
2. No data migration: tracks and the search read model keep their shape.
3. Rollback: revert the commit. The extra Keycloak role and claim are ignored by the old code.
