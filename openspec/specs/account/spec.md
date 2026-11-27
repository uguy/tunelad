# account Specification

## Purpose
Identify the people using Tune Lad: log them in through an OpenID Connect provider from the browser or with a bearer
token, keep an account for each identity, and tell the UI who is logged in.

## Requirements

### Requirement: Log in through an OpenID Connect provider
The system SHALL let a browser user log in through any configured OpenID Connect provider. `/login` is a page of the
application, with its header and styling, offering a login button for each configured provider; the providers are
also available as JSON from `GET /api/login-providers`, which needs no authentication. `/oauth2/authorization/{provider}`
starts the login. After the provider authenticates the user, the system SHALL establish a session and redirect the
browser back to the application.

#### Scenario: Start a login
- **WHEN** a browser requests `/oauth2/authorization/keycloak`
- **THEN** the response redirects to the Keycloak realm's authorization endpoint

#### Scenario: Provider list
- **WHEN** a browser requests `/login`
- **THEN** the page, shown inside the application's layout, offers a login button for each configured provider

#### Scenario: Provider list as JSON
- **WHEN** an anonymous client requests `GET /api/login-providers`
- **THEN** the response status is 200 OK
- **AND** the body lists each configured provider with its `id` and display `name`

#### Scenario: No provider configured
- **GIVEN** no login provider is configured
- **WHEN** a browser requests `/login`
- **THEN** the page says that no login provider is configured

#### Scenario: Successful login
- **GIVEN** the user `alice` exists in the identity provider
- **WHEN** alice completes the login at the provider
- **THEN** the browser is redirected back to the application with a session
- **AND** `GET /api/me` in that session returns alice's account

### Requirement: Authenticate API calls with a bearer token
The system SHALL accept an access token issued by the configured Keycloak realm in an `Authorization: Bearer` header
as authentication for API calls, and SHALL reject a missing, malformed, expired or foreign token with 401
Unauthorized on endpoints that require authentication.

#### Scenario: Valid token
- **GIVEN** a valid access token for alice from the Keycloak realm
- **WHEN** a client requests `GET /api/me` with that bearer token
- **THEN** the response status is 200 OK and the body is alice's account

#### Scenario: Invalid token
- **WHEN** a client requests `GET /api/me` with a bearer token that is not signed by the realm
- **THEN** the response status is 401 Unauthorized

### Requirement: One account per identity
The system SHALL create an account the first time an identity authenticates, by browser login or bearer token, and
SHALL return the same account every later time that identity authenticates. An identity is the provider's issuer
and subject, so the same person through two providers has two accounts.

#### Scenario: First authentication creates an account
- **GIVEN** alice has never authenticated
- **WHEN** alice requests `GET /api/me` authenticated
- **THEN** the response contains an account with a non-empty `id`

#### Scenario: Same identity, same account
- **GIVEN** alice has already authenticated once
- **WHEN** alice authenticates again and requests `GET /api/me`
- **THEN** the account `id` is the same as the first time

#### Scenario: Different identities, different accounts
- **WHEN** alice and bob each request `GET /api/me` authenticated
- **THEN** their account ids differ

### Requirement: Account display name from the identity
The system SHALL give every account a `displayName` taken from the identity's claims: the full name if present,
otherwise the preferred username, otherwise the email address, otherwise the subject. The system SHALL refresh it
from the claims on every browser login.

#### Scenario: Name claim present
- **GIVEN** alice's identity has the name "Alice Liddell" and the username `alice`
- **WHEN** alice requests `GET /api/me` authenticated
- **THEN** `displayName` is "Alice Liddell"

#### Scenario: Only a username
- **GIVEN** an identity with the username `carol` and no name claim
- **WHEN** carol requests `GET /api/me` authenticated
- **THEN** `displayName` is "carol"

#### Scenario: Name changed at the provider
- **GIVEN** alice has an account with display name "Alice Liddell"
- **AND** her name at the provider is now "Alice L."
- **WHEN** alice logs in again from the browser
- **THEN** `GET /api/me` returns `displayName` "Alice L."

### Requirement: Current account endpoint
The system SHALL return the authenticated user's account on `GET /api/me` as a JSON object with `id`,
`displayName` and `admin` (true when the user is an administrator, false otherwise), and SHALL respond 401
Unauthorized, without redirecting, when the request is not authenticated.

#### Scenario: Authenticated
- **WHEN** an authenticated user requests `GET /api/me`
- **THEN** the response status is 200 OK and the body has their `id`, `displayName` and `admin`

#### Scenario: Anonymous
- **WHEN** a client without a session or token requests `GET /api/me`
- **THEN** the response status is 401 Unauthorized and the response is not a redirect

### Requirement: Log out
The system SHALL end the browser session on `POST /logout`, also end the user's session at the identity provider,
and redirect the browser back to the application.

#### Scenario: Log out from the browser
- **GIVEN** alice is logged in from the browser
- **WHEN** alice posts to `/logout` with a valid CSRF token
- **THEN** the browser is redirected through the provider's logout back to the application
- **AND** `GET /api/me` in that browser responds 401 Unauthorized

### Requirement: CSRF protection for browser sessions
The system SHALL reject a state-changing request (POST, PUT, PATCH, DELETE) that is authenticated by a browser
session but carries no valid CSRF token, with 403 Forbidden. It SHALL give the browser the token in a readable
`XSRF-TOKEN` cookie and accept it back in the `X-XSRF-TOKEN` header. Requests authenticated by a bearer token SHALL
not need a CSRF token.

#### Scenario: Session request without a token
- **GIVEN** alice is logged in from the browser
- **WHEN** her browser posts a track upload without the `X-XSRF-TOKEN` header
- **THEN** the response status is 403 Forbidden and no track is stored

#### Scenario: Session request with a token
- **GIVEN** alice is logged in from the browser
- **WHEN** her browser posts a track upload with the `X-XSRF-TOKEN` header matching the `XSRF-TOKEN` cookie
- **THEN** the upload succeeds

#### Scenario: Bearer request
- **WHEN** a client uploads a track with a valid bearer token and no CSRF token
- **THEN** the upload succeeds

### Requirement: Login state in the header
The UI header SHALL show a "Log in" link to `/login` when nobody is logged in. When someone is logged in it SHALL
show an account menu button labelled with the user's display name, which opens a menu with a link to the account
details page (`/account`), links to their favourites and playlists, and a "Log out" control.

#### Scenario: Anonymous visitor
- **GIVEN** `GET /api/me` responds 401 Unauthorized
- **WHEN** a visitor opens the application
- **THEN** the header shows a "Log in" link to `/login`
- **AND** no account menu and no "Log out" control is shown

#### Scenario: Logged-in user
- **GIVEN** `GET /api/me` returns an account with display name "Alice Liddell"
- **WHEN** the user opens the application
- **THEN** the header shows an account menu button labelled "Alice Liddell"
- **AND** no "Log in" link is shown

#### Scenario: Account menu
- **GIVEN** the user is logged in
- **WHEN** the user opens the account menu
- **THEN** it offers links to `/account`, `/tracks/favourites` and `/tracks/playlists`, and a "Log out" control

#### Scenario: Log out from the header
- **GIVEN** the user is logged in
- **WHEN** the user opens the account menu and activates "Log out"
- **THEN** the browser posts to `/logout` with the CSRF token

### Requirement: Administrators come from the identity provider's admin role
The system SHALL treat a user as an administrator when the identity provider grants them the `admin` role in the
`roles` claim of their bearer token or browser login, and as a regular user otherwise. A role change at the provider
SHALL apply to bearer tokens issued after it and to browser sessions started after it.

#### Scenario: Token with the admin role
- **GIVEN** a valid bearer token whose `roles` claim contains `admin`
- **WHEN** the client requests `GET /api/me` with it
- **THEN** the body has `admin` true

#### Scenario: Token without the admin role
- **GIVEN** a valid bearer token for alice with no `roles` claim, or one without `admin`
- **WHEN** alice requests `GET /api/me` with it
- **THEN** the body has `admin` false

#### Scenario: Local realm administrator
- **GIVEN** the local Keycloak realm's user `admin`
- **WHEN** that user gets an access token from the realm and requests `GET /api/me` with it
- **THEN** the body has `admin` true
- **AND** the same request with a token for `alice` has `admin` false

#### Scenario: Browser login with the admin role
- **GIVEN** a user whose identity carries the `admin` role logs in from the browser
- **WHEN** their browser requests `GET /api/me`
- **THEN** the body has `admin` true

### Requirement: Account details page
The UI SHALL show the logged-in user's details on `/account`: their display name, their role ("Administrator" or
"Member"), the number of their favourite tracks and the number of their playlists, each linking to its page, and a
"Log out" control. The page SHALL be reachable from the header's account menu. For an anonymous visitor it SHALL
offer a link to log in instead.

#### Scenario: Details shown
- **GIVEN** `GET /api/me` returns "Alice Liddell" with `admin` false, `GET /api/me/favourites` returns 4 tracks and
  `GET /api/playlists` returns 2 playlists
- **WHEN** the user opens `/account`
- **THEN** the page shows "Alice Liddell", the role "Member", "4 favourites" linking to `/tracks/favourites` and
  "2 playlists" linking to `/tracks/playlists`

#### Scenario: Administrator
- **GIVEN** `GET /api/me` returns an account with `admin` true
- **WHEN** the user opens `/account`
- **THEN** the page shows the role "Administrator"

#### Scenario: Log out from the page
- **GIVEN** the user is logged in and on `/account`
- **WHEN** the user activates "Log out"
- **THEN** the browser posts to `/logout` with the CSRF token

#### Scenario: Anonymous visitor
- **GIVEN** `GET /api/me` responds 401 Unauthorized
- **WHEN** a visitor opens `/account`
- **THEN** the page offers a "Log in" link and shows no account details
