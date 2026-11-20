# Tasks

## 1. Branch

- [x] 1.1 Update `main` (`git switch main && git pull`) and create `feat/user-accounts` from it. Verify `git status` shows the new branch with a clean tree (deal with any uncommitted local changes first)

## 2. Local Keycloak

Commit: `build(account): add keycloak with a tunelad realm for local login`

- [x] 2.1 Add `docker/keycloak/tunelad-realm.json`: realm `tunelad`, confidential client `tunelad` (dev-only secret, standard flow, redirect and post-logout URIs for `:8080` and `:5173`, direct access grants, audience mapper adding `tunelad`), and users `alice` ("Alice Liddell") and `bob` ("Bob Smith") with each password equal to the username. Mark it dev-only in its description field
- [x] 2.2 Add a `keycloak` service to `docker-compose.yaml` (pinned `quay.io/keycloak/keycloak`, `start-dev --import-realm`, host port 8180, realm mounted). Verify `docker compose up -d keycloak`, then `curl localhost:8180/realms/tunelad/.well-known/openid-configuration` returns JSON, and a password-grant token request for `alice` succeeds and its access token's `aud` contains `tunelad`
- [x] 2.3 Document the Keycloak service, its URL, the test users and the dev-only nature of the realm in README.adoc's "Developing" section. Verify the documented commands run as written

## 3. Account module with bearer authentication and `/api/me`

Commit: `feat(account): add accounts resolved from oidc identities and get /api/me`

- [x] 3.1 Add `spring-boot-starter-security`, `spring-boot-starter-oauth2-client`, `spring-boot-starter-oauth2-resource-server` and test-scoped `spring-security-test` to `pom.xml`. Verify `./mvnw -DskipTests verify` compiles
- [x] 3.2 Add `infrastructure.web.security.SecurityConfig` with the resource server (issuer `http://localhost:8180/realms/tunelad`, audience `tunelad`), `GET /api/me` authenticated, 401 entry point on `/api/**`, and everything else still `permitAll` for now (uploads stay open until group 5). Configure it in `application.yaml`
- [x] 3.3 Create the `account` module: `package-info.java` (`allowedDependencies = "infrastructure"`), public `CurrentAccount` and `AccountRef`, internal `Account` document (unique `(issuer, subject)` index), reactive `Accounts` repository, `DisplayNames` (name, preferred_username, email, sub) and `DefaultCurrentAccount` (find or register by `(issuer, subject)` from a `Jwt` or `OidcUser`, with an idempotent upsert and a duplicate-key retry). Verify `ModularApplicationTests` passes
- [x] 3.4 Add `account.web.AccountController` with `GET /api/me` returning `{id, displayName}`, an `Account` OpenAPI tag, and a `bearerAuth` security scheme applied to it with a documented 401. Verify `/v3/api-docs` shows the endpoint and the scheme
- [x] 3.5 Add test support: a `TestSecurityConfiguration` with a `ReactiveJwtDecoder` on a test-generated RSA key, a `TestTokens.bearer(username, name)` helper, and test properties with explicit provider endpoints (no `issuer-uri`), imported by `AbstractApiTest`. Verify the existing Java tests still pass
- [x] 3.6 Add `AccountApiTest` covering the spec's "Authenticate API calls with a bearer token", "One account per identity", "Current account endpoint" and the bearer side of "Account display name from the identity" (name and username-only cases), plus a `DisplayNames` unit test for the email and sub fallbacks. Verify they pass

## 4. Browser login, CSRF and logout

Commit: `feat(account): log in and out from the browser through oidc`

- [x] 4.1 Extend `SecurityConfig` with `oauth2Login()` (keycloak registration in `application.yaml`), a redirect-to-`/login` entry point outside `/api/**`, the cookie CSRF repository with `ServerCsrfTokenRequestAttributeHandler`, the WebFilter that subscribes to the CSRF token, a CSRF matcher skipping `Bearer` requests, and `OidcClientInitiatedServerLogoutSuccessHandler` with `{baseUrl}/`
- [x] 4.2 Add the internal `AccountRegistering` bean that decorates `OidcReactiveOAuth2UserService` to upsert the account and refresh `displayName`/`lastLoginAt` on login, with a unit test for the spec's "Name changed at the provider" scenario
- [x] 4.3 Add `application-google.yaml` with a `google` registration reading `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`, and document how to enable it in README.adoc. Verify the app starts with the profile and fake credentials, and `/login` lists both providers
- [x] 4.4 Make `AbstractApiTest`'s upload helper send alice's bearer token (CSRF now rejects anonymous POSTs, and bearer requests are exempt), then add `BrowserSessionSecurityTest` (WebTestClient bound to the application context with `springSecurity()`, `mockOidcLogin()`, `csrf()`) covering: `/oauth2/authorization/keycloak` redirects to the realm's authorization endpoint, `/login` lists providers, `/api/me` with a session, a session POST without a CSRF token gets 403, the `XSRF-TOKEN` cookie is issued, and logout ends the session. Verify it passes
- [x] 4.5 Manually check against the compose Keycloak: log in as alice at `http://localhost:8080/login`, `GET /api/me` returns "Alice Liddell", `POST /logout` brings you back and `/api/me` gives 401

## 5. Track owner and authenticated uploads

Commit: `feat(track)!: require authentication to upload and record the track owner`

- [x] 5.1 Add `TrackOwner(id, displayName)` to the `track` package, add `owner` to `TrackDTO`, `AddTrackCommand` and the `Track` document, and update `TrackMapper`. Add `"account"` to `track`'s `allowedDependencies`. Verify `ModularApplicationTests` passes
- [x] 5.2 Make `TrackController.create` resolve `CurrentAccount` and pass the owner into the command, require authentication for `POST /api/tracks` in `SecurityConfig`, and document `bearerAuth`, 401 and 403 on the operation in OpenAPI
- [x] 5.3 Add `owner` to `SearchTrack` and `SearchTrack.of`
- [x] 5.4 Add an `AbstractApiTest` upload overload taking a user's token, and add tests for the spec's "Uploading requires authentication", "Every track has an owner", "Browse tracks without logging in", the updated upload and list scenarios, "Search without logging in", the updated "Searchable track has the track list fields" and "Play without logging in" (anonymous playback). Verify `./mvnw verify -DskipITs` passes
- [x] 5.5 Update the disabled `ImporterTest` to fetch a bearer token for alice from the dev realm, and add to README.adoc a curl recipe for uploading with a token plus the `docker compose down -v` wipe step for existing data. Verify the curl recipe uploads a track against the running app

## 6. UI login state and track owners

Commit: `feat(ui): show login state in the header and the owner of each track`

- [x] 6.1 Make `app/src/lib/api.ts` send `X-XSRF-TOKEN` from the `XSRF-TOKEN` cookie on non-GET requests and drop the unused `Token` header parameter. Add a `me` store loaded from `GET /api/me` (401 means anonymous)
- [x] 6.2 Update `Header.svelte` for both desktop and mobile menus: a "Log in" link to `/login` when anonymous, and the display name plus a "Log out" form (POST `/logout` with a hidden `_csrf`) when logged in
- [x] 6.3 Show the owner's display name for each track in `TrackList.svelte`
- [x] 6.4 Add `/login`, `/oauth2` and `/logout` to the Vite dev proxy, and add `/login`, `/oauth2/`, `/logout` and `/api/` to the PWA `navigateFallbackDenylist`
- [x] 6.5 Add Playwright tests for "Login state in the header" (anonymous, logged in, logout posts to `/logout` with the token), "Show who uploaded each track" and "Anonymous link with a start time". Mock `/api/me` as anonymous and add `owner` to the fixtures in the existing tests. Run `npm run format` and `npm run lint`, and verify `npm run test:e2e` passes

## 7. Real Keycloak integration test

Commit: `test(account): verify login and bearer tokens against a real keycloak`

- [ ] 7.1 Add the `testcontainers-keycloak` test dependency (check compatibility with Testcontainers 2.x, or fall back to a `GenericContainer` per design.md) and wire the container into a `KeycloakIntegrationTest` importing `docker/keycloak/tunelad-realm.json`, with issuer and client properties registered dynamically
- [ ] 7.2 In that test, get alice's token with the password grant, then assert `GET /api/me` returns "Alice Liddell", an upload with the token records alice as owner, and `/oauth2/authorization/keycloak` redirects to the container's authorization endpoint. Verify it passes

## 8. Finish

- [ ] 8.1 Add "Log in with an OpenID Connect provider (Keycloak locally, Google optional)" and "Tracks show who uploaded them; uploading requires login or a bearer token" to README.adoc's Features
- [ ] 8.2 Run `./mvnw clean verify` and verify it passes, including `ModularApplicationTests`, the Keycloak test and the Playwright e2e tests
- [ ] 8.3 Smoke-test the built app with `docker compose up -d`: anonymous browse, search and play, log in as alice, upload with a token, see "Alice Liddell" on the track, log out. Check that the service worker doesn't intercept `/login`
- [ ] 8.4 Push `feat/user-accounts` and open a PR to `main` titled `feat(account)!: add user accounts with oidc login and track owners`, noting the breaking upload change and the data wipe in the description

## Workflow follow-up

- Archive the change with `/openspec-archive-change` on the feature branch before merging, so specs land in `main` with the code.
- Then start the follow-up change `track-ownership-control` (owner-only edit and delete).
