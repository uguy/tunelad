# Proposal

## Why

Anyone can upload to Tune Lad, and nothing records who uploaded what. Owners will need to edit and delete their own
tracks, but that needs a notion of who a user is first. This change adds accounts: login through an OpenID Connect
provider, with Keycloak running locally in place of Google and friends, and every new track records its owner.
Owners editing and deleting their tracks comes in a follow-up change, `track-ownership-control`.

## What Changes

- Log in from the browser through an OpenID Connect provider, using a server-side session. Keycloak runs in
  `docker compose` with a ready-made realm and a test user. Google can be turned on as a second provider by
  supplying its client credentials.
- Scripts and API clients can call the API with a bearer access token issued by the same Keycloak realm.
- Log out from the browser, which also ends the session at the identity provider.
- New `account` module: an account is created the first time someone authenticates, and is refreshed when they log
  in again. It is identified by the identity provider's issuer and subject.
- New endpoint `GET /api/me` returns the current account (`id`, `displayName`), or 401 when not authenticated.
- **BREAKING**: `POST /api/tracks` requires an authenticated user (session or bearer token) and responds 401
  otherwise. Browser-session requests that change state also need a CSRF token.
- Every track gets a required `owner` (`id`, `displayName`), set from the uploader at upload time. It appears in
  `GET /api/tracks`, `GET /api/tracks/{id}` and search results.
- Listing, fetching, searching and playing tracks stay open to anonymous visitors.
- UI: the header shows a "Log in" link for anonymous visitors, and for logged-in users their display name plus
  "Log out". Each track in the list shows who uploaded it.
- **BREAKING (data)**: tracks stored before this change have no owner and aren't migrated. The local database is
  wiped with `docker compose down -v`, which is documented in the README.

Affected Modulith modules: `account` (new), `track`, `search`, `infrastructure` (security configuration). `media`
code doesn't change, but its spec gains an explicit anonymous playback requirement. Touches both the REST API and
the UI.

## Capabilities

### New Capabilities

- `account`: authenticating users through OpenID Connect (browser login and logout, bearer tokens), the account
  record created from the identity, the current-account endpoint, CSRF protection for browser sessions, and the
  header login state in the UI.

### Modified Capabilities

- `track`: uploading requires an authenticated user and records them as the track's owner. Tracks carry an `owner`
  in every response. Browsing stays anonymous. The track list in the UI shows each track's uploader.
- `search`: search results carry the track's `owner`, like the track list. Searching stays anonymous.
- `media`: states explicitly that playing a track needs no login.

## Out of scope

- Editing and deleting tracks, and owner-only checks (follow-up change `track-ownership-control`)
- Private tracks
- An upload form in the UI (uploads stay API-only, now authenticated)
- A "My tracks" page or owner filter on list and search
- An admin role or any role-based authorization
- Account linking (the same person through Keycloak and through Google gets two accounts)
- GitHub or other non-OIDC providers
- Profile editing and avatars, and propagating display-name changes to tracks already stored
- Persistent sessions (sessions are in memory and lost on restart)
- A custom login page (Spring Security's generated provider list is used)
- Migrating tracks that have no owner

## Impact

- **REST API**: new `GET /api/me`. `POST /api/tracks` now returns 401 without authentication and 403 when a
  browser-session request has no CSRF token. `TrackDTO` gains `owner`. New non-API routes `/login`,
  `/oauth2/authorization/{provider}`, `/login/oauth2/code/{provider}` and `/logout`. The OpenAPI description
  gains security schemes and the new endpoint.
- **Dependencies**: Spring Security (OAuth2 client and resource server), `spring-security-test`, and a Keycloak
  Testcontainers module.
- **Infrastructure**: a Keycloak service and realm export in `docker-compose.yaml` and `docker/keycloak/`. The app
  now needs Keycloak running to start locally. The Vite dev proxy forwards the login and logout routes.
- **Data**: new `accounts` collection. `tracks` and `search_tracks` documents gain `owner`. Existing local data
  must be wiped.
- **Tests**: API tests authenticate uploads. New tests cover the access rules, CSRF and the account endpoint, plus
  one test against a real Keycloak container. Playwright tests mock `/api/me`.
- **PR title**: `feat(account)!: add user accounts with oidc login and track owners`
