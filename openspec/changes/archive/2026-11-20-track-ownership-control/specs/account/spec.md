# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
