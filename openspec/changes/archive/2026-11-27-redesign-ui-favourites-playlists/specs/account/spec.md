## ADDED Requirements

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

## MODIFIED Requirements

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
