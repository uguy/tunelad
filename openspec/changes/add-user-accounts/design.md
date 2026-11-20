# Design

## Context

See proposal.md for motivation. These are the parts of the current system that shape the approach:

- **No security at all.** Spring Security isn't on the classpath. `WebConfig` allows CORS from any origin.
- **The SPA and the API share an origin.** The SvelteKit app is prerendered (`ssr = false`) and served by the Spring
  Boot app. `SpaWebFilter` rewrites `/tracks` and `/about` to `/`. In dev, Vite proxies `/api` and `/actuator` to
  `:8080`.
- **Audio plays through `<audio src="/api/tracks/{id}/play">`.** The browser sends cookies with that request but
  can't add an `Authorization` header. Playback stays anonymous, so this doesn't bite yet.
- **Module wiring.** `track` calls `media` synchronously (`MediaStore.store`) and publishes
  `NewTrackSaved(TrackDTO)`. `search` projects that event into its own `search_tracks` collection.
  `ModularApplicationTests` checks the dependencies.
- **Tests.** `AbstractApiTest` runs the full app on a random port and talks to it through
  `WebTestClient.bindToServer()`. That makes it a black-box client, so Spring Security's `mutateWith(mockOidcLogin())`
  test helpers do **not** apply to it. Playwright tests run against `vite preview` with every API call mocked by
  `page.route`.
- **There is no upload UI.** Uploads go through the API (curl, Scalar). The disabled `ImporterTest` is a script for
  this.

## Goals / Non-Goals

**Goals:**

- Run browser login (session) and API bearer tokens side by side, both resolving to the same account.
- Keep `infrastructure` free of any dependency on `account`, and keep `track` free of Spring Security types.
- Run every Java test except one without a real identity provider, keeping the black-box API tests black-box.
- Keep everything reactive.

**Non-Goals:**

- Authorization beyond "authenticated or not". Owner-only rules come in the follow-up change.
- Production hardening: secrets management, a persistent session store, a deployed Keycloak.

## Decisions

### 1. Session login and resource-server JWT in one filter chain

There is one `SecurityWebFilterChain` with both `oauth2Login()` (browser, `SESSION` cookie) and
`oauth2ResourceServer().jwt()` (API clients). A request carrying a `Bearer` header is authenticated by the resource
server. Any other request uses the session.

- *Alternative: session only.* That leaves no reasonable way to upload from scripts once there's no upload UI.
  The user chose bearer support.
- *Alternative: JWT only, with the SPA holding tokens.* Tokens would live in browser JS, and `<audio src>` can't
  send them. That conflicts with the same-origin BFF shape this app already has.

**Authorization rules** live in `infrastructure.web.security.SecurityConfig`, next to `SpaWebFilter`, which already
knows the app's routes:

```
GET  /api/tracks, /api/tracks/{id}, /api/tracks/search, /api/tracks/{id}/play   permitAll
POST /api/tracks                                                                authenticated
GET  /api/me                                                                    authenticated
/api/**  (anything else)                                                        authenticated
/actuator/**, /v3/api-docs/**, /scalar/**, static assets, SPA routes            permitAll
```

**Entry points:** `/api/**` answers 401 without redirecting (the resource server's bearer entry point, which adds
`WWW-Authenticate: Bearer`). Everything else redirects to `/login`.

### 2. Keycloak only, OIDC only (B-lite)

Spring Boot properties define one client registration, `keycloak`, pointing at the issuer
`http://localhost:8180/realms/tunelad`. The resource server trusts the same issuer and checks that the access token
audience contains `tunelad`. An `application-google.yaml` profile adds a `google` registration that reads
`GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` from the environment. Google is browser-login only, since bearer
tokens come only from Keycloak.

The "Log in" link points at `/login`, Spring Security's generated page, which lists the configured registrations.
That avoids hard-coding a provider in the SPA when the `google` profile is on.

- *Alternative: link straight to `/oauth2/authorization/keycloak`.* It saves a click, but it would break the moment a
  second provider is enabled. A custom styled login page is out of scope.

### 3. Identity is `(issuer, subject)`, and accounts live in a new `account` module

New Modulith module `org.tunelad.account` with `allowedDependencies = { "infrastructure" }`:

```
account/                       public API (module base package)
  CurrentAccount               Mono<AccountRef> get(): resolves the authenticated principal into an account
  AccountRef                   record(id, displayName)
  web/AccountController        GET /api/me
  internal/
    Account                    @Document("accounts"): id, issuer, subject, displayName, email, createdAt, lastLoginAt
                               unique compound index (issuer, subject)
    Accounts                   reactive repository
    DefaultCurrentAccount      reads ReactiveSecurityContextHolder, takes iss/sub/claims from OidcUser or Jwt
    AccountRegistering         ReactiveOAuth2UserService<OidcUserRequest, OidcUser> bean decorating
                               OidcReactiveOAuth2UserService: upserts the account on every browser login
    DisplayNames               claims -> displayName (name, preferred_username, email, sub)
```

There are two paths into the account, and both are idempotent:

- **Browser login.** The `AccountRegistering` bean is picked up automatically by `oauth2Login()`, so
  `infrastructure` never references `account`. It upserts the account and refreshes `displayName` and
  `lastLoginAt`.
- **Request time.** `CurrentAccount.get()` looks up the account by `(issuer, subject)` and registers it if it's
  missing. This covers bearer clients who never log in through the browser. It also covers tests using
  `mockOidcLogin()`, which bypasses the user service, and sessions that outlive a database wipe.

Registration uses an upsert with `$setOnInsert` on the unique index. A concurrent duplicate-key error is retried as
a lookup.

- *Alternative: claims only, with no account record.* Rejected during exploration: there would be no stable
  internal id and nowhere to keep a profile.
- *Alternative: an `AccountRegistered` event.* No module consumes it yet. It can be added when one does.

### 4. Owner is a snapshot on the track, mapped into `track`'s own type

`track` gains `allowedDependencies += "account"`. `TrackController.create` calls `CurrentAccount.get()` and passes
the result into `AddTrackCommand` as a `TrackOwner(id, displayName)`. `TrackOwner` is a new record in the `track`
package, and `TrackDTO` gains `TrackOwner owner`.

`track` maps the account into its own type rather than exposing `AccountRef`. That keeps `account` types out of
`TrackDTO` and `NewTrackSaved`, so `search`, which depends only on `track`, doesn't need a dependency on `account`.
`SearchTrack` gains `owner` from the event, through the existing `SearchTrack.of(TrackDTO)`.

The display name is a snapshot taken at upload time, and nothing updates it after a rename. That's an accepted
limitation (see the proposal's Out of scope).

### 5. CSRF: cookie token readable by the SPA, skipped for bearer requests

- `CookieServerCsrfTokenRepository.withHttpOnlyFalse()` sets the `XSRF-TOKEN` cookie. A
  `ServerCsrfTokenRequestAttributeHandler` (not the XOR variant) accepts the raw cookie value in `X-XSRF-TOKEN` or
  the `_csrf` form field.
- WebFlux resolves the token lazily, so a small `WebFilter` subscribes to it on every response. Without it, the
  cookie never gets written for a SPA that never renders a server form.
- `requireCsrfProtectionMatcher` skips requests carrying an `Authorization: Bearer` header. A bearer token isn't
  sent automatically by a browser, so CSRF doesn't apply to it. This is set explicitly rather than relying on
  defaults.
- SPA: `api.ts` copies the cookie into `X-XSRF-TOKEN` on non-GET requests. The unused `Token` header parameter is
  removed.

### 6. Logout through the provider

`POST /logout` uses `OidcClientInitiatedServerLogoutSuccessHandler` with `postLogoutRedirectUri = "{baseUrl}/"`.
Keycloak ends its own session too, so the next login prompts for credentials. Google has no `end_session_endpoint`,
so the handler falls back to redirecting to `/`. The header's "Log out" is a small `<form method="post"
action="/logout">` with a hidden `_csrf` field: a plain full-page navigation, with no fetch and no JS redirect
handling.

### 7. Local Keycloak and the shared realm

- `docker-compose.yaml` gains `keycloak` (`quay.io/keycloak/keycloak`, pinned version,
  `start-dev --import-realm`) on host port **8180**, because 8080 is the app and 8081 is Mongo Express. It mounts
  `docker/keycloak/` at `/opt/keycloak/data/import`.
- `docker/keycloak/tunelad-realm.json` sets up the following:
  - realm `tunelad`
  - a confidential client `tunelad` with a dev-only secret committed in the file
  - the standard flow, with redirect URIs for `http://localhost:8080/*` and `http://localhost:5173/*`
    (the Vite dev server) and matching post-logout redirect URIs
  - direct access grants enabled, so the README can show getting a token with curl
  - an audience mapper that adds `tunelad` to access tokens
  - users `alice` ("Alice Liddell") and `bob` ("Bob Smith"), with each password equal to the username
- The Keycloak Testcontainer imports this same file, so dev and tests share one realm.
- Vite's dev proxy adds `/login`, `/oauth2` and `/logout`. The proxy keeps the `Host` header, so Spring builds
  `{baseUrl}` redirect URIs on `:5173`, which the realm allows.
- The PWA service worker (`generateSW`) serves `index.html` for navigations. `/login`, `/oauth2/`, `/logout` and
  `/api/` are added to `navigateFallbackDenylist`, so login navigations reach the server.

### 8. OpenAPI

- springdoc declares a `bearerAuth` security scheme (HTTP bearer, JWT).
- `POST /api/tracks` and `GET /api/me` carry it as a security requirement and document `401`.
  `POST /api/tracks` also documents `403` for a missing CSRF token on session requests.
- `/api/me` is under a new `Account` tag.
- `TrackDTO`'s schema gains `owner`.
- Scalar can then send a bearer token pasted by the user.

### 9. Testing strategy

| What | How |
|---|---|
| API access rules, owner, `/api/me`, accounts | Existing `AbstractApiTest` style (black box, `bindToServer`). A `TestSecurityConfiguration` replaces the `ReactiveJwtDecoder` with one backed by an RSA key generated for the test run, and a `TestTokens.bearer("alice", "Alice Liddell")` helper mints matching JWTs. Upload helpers send a default user's token. No CSRF is needed, because bearer requests skip it. |
| Session login, CSRF, logout, `/login`, authorization redirect | A dedicated test class binding `WebTestClient` to the application context with `springSecurity()`, using the `mockOidcLogin()` and `csrf()` mutators. |
| Display-name rules and refresh on login | Unit tests for `DisplayNames` and the `AccountRegistering` decorator. |
| Real Keycloak | One `KeycloakIntegrationTest` with `testcontainers-keycloak`, importing the shared realm and registering issuer properties dynamically. It gets alice's token with the password grant, calls `/api/me` and uploads a track. It also checks `/oauth2/authorization/keycloak` redirects to the container's authorization endpoint. |
| UI | Playwright mocks `/api/me` (401 or 200) for the header states and the logout form. The track fixture gains `owner`. Existing tests mock `/api/me` as anonymous. |
| Module boundaries | `ModularApplicationTests`, unchanged. It must keep passing with the new `account` module and the `track -> account` dependency. |

The test `application` properties give the `keycloak` registration explicit `authorization-uri`, `token-uri`,
`jwk-set-uri` and `user-info-uri`, with no `issuer-uri`. That stops the contexts from trying OIDC discovery against a
Keycloak that isn't running at startup.

### 10. Reactive throughout

Account storage uses Spring Data reactive Mongo. `CurrentAccount` reads `ReactiveSecurityContextHolder`. The user
service decorator is the reactive `ReactiveOAuth2UserService`. No blocking calls are added to request paths.

## Risks / Trade-offs

- [The app can't start without Keycloak, because client registration does OIDC discovery at startup] → Keycloak is
  part of `docker compose up -d`. The README says so. Tests avoid discovery with explicit endpoints (decision 9).
- [`testcontainers-keycloak` may lag behind Testcontainers 2.x or the Keycloak image version] → Check compatibility
  when adding it. The fallback is a `GenericContainer` on the Keycloak image, with `--import-realm` and a wait on
  `/realms/tunelad`.
- [Committed client secret and password grant enabled in the realm] → They only exist in the dev realm, which
  never leaves local Docker. Both the realm file and the README say so.
- [In-memory sessions, so a restart logs everyone out] → Accepted for a local playground. Spring Session with Mongo
  is listed as a follow-up.
- [Owner display names go stale after a rename at the provider] → Accepted. An `AccountRenamed` event can update
  tracks and search later.
- [The service worker could swallow login navigations] → `navigateFallbackDenylist` (decision 7), covered by a
  manual check in the final verification since Playwright runs without the backend.
- [Breaking for API scripts, including the disabled `ImporterTest`] → `ImporterTest` gets a bearer token from the
  dev realm, and the README shows a curl upload with a token.
- [Old local data has tracks without an owner, which would break the "owner is always present" contract] → The
  migration plan wipes the data. The code doesn't tolerate missing owners.

## Migration Plan

1. `docker compose down -v` deletes the anonymous Mongo volume: tracks, GridFS audio, search read model and the
   event publication registry.
2. `docker compose up -d` brings Mongo up together with Keycloak, which imports the `tunelad` realm on first start.
3. Start the app. Log in as `alice`/`alice`, or get a token with curl (README) and upload.

Rollback: revert the merge commit. The old code ignores the `owner` fields and the `accounts` collection, so no wipe
is needed.
