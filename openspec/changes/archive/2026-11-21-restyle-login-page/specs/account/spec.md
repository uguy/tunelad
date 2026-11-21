## MODIFIED Requirements

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
