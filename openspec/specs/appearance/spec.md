# appearance Specification

## Purpose
TBD - created by archiving change redesign-ui-favourites-playlists. Update Purpose after archive.

## Requirements

### Requirement: Light and dark themes in one palette
The UI SHALL offer a light theme and a dark theme that share the application's orange accent and charcoal header, and
SHALL apply the active theme to every page and component, including the player bar, forms, cards, waveforms and
error messages. In both themes, text SHALL reach a contrast ratio of at least 4.5:1 against its background (3:1 for
large text and icons), and every interactive element SHALL show a visible focus indicator.

#### Scenario: Dark theme applied everywhere
- **GIVEN** the dark theme is active
- **WHEN** the user visits the home, tracks, upload, login, account, favourites and playlists pages
- **THEN** each page uses the dark surfaces and light text of the dark theme, with no light-theme panel left

#### Scenario: Focus is visible
- **WHEN** the user moves through a page with the keyboard in either theme
- **THEN** the focused element shows a visible focus ring

### Requirement: System theme by default
The UI SHALL use the theme the operating system or browser asks for (`prefers-color-scheme`) when the user has made
no choice, and SHALL follow a change of that setting while the page is open.

#### Scenario: System prefers dark
- **GIVEN** the user has never chosen a theme and the system prefers a dark colour scheme
- **WHEN** the user opens the application
- **THEN** the dark theme is active

#### Scenario: System prefers light
- **GIVEN** the user has never chosen a theme and the system prefers a light colour scheme
- **WHEN** the user opens the application
- **THEN** the light theme is active

#### Scenario: System setting changes
- **GIVEN** the user has never chosen a theme and the application is open in the light theme
- **WHEN** the system switches to a dark colour scheme
- **THEN** the application switches to the dark theme without a reload

### Requirement: Theme switch
The header SHALL contain a theme switch, reachable on desktop and in the mobile menu, that is a button with an
accessible name stating what it does and a state telling which theme is active. Activating it SHALL switch between
the light and dark themes immediately and remember that choice in the browser, overriding the system setting until
the user chooses "System" again. The switch SHALL offer a way back to following the system.

#### Scenario: Switch to dark
- **GIVEN** the light theme is active
- **WHEN** the user activates the theme switch and picks dark
- **THEN** the dark theme is active immediately

#### Scenario: Choice is remembered
- **GIVEN** the user picked the dark theme while the system prefers light
- **WHEN** the user reloads the application
- **THEN** the dark theme is active

#### Scenario: Back to the system theme
- **GIVEN** the user picked the dark theme while the system prefers light
- **WHEN** the user picks "System" in the theme switch
- **THEN** the light theme is active and the stored choice is cleared

### Requirement: No flash of the wrong theme
The UI SHALL apply the theme before the first paint, so that a user whose theme is dark never sees the light theme
flash while the page loads, and SHALL set the browser's `color-scheme` and `theme-color` to match the active theme.

#### Scenario: Reload in dark theme
- **GIVEN** the dark theme is active
- **WHEN** the page is reloaded
- **THEN** the first rendered frame already uses the dark background

### Requirement: Responsive layout
The UI SHALL be usable from a 360 px wide phone to a wide desktop: navigation collapses into a menu on small screens,
track rows keep their controls reachable with touch-size targets, the player bar never covers page content, and
nothing scrolls horizontally. Animations SHALL be disabled for users who prefer reduced motion.

#### Scenario: Phone width
- **GIVEN** the viewport is 360 px wide
- **WHEN** the user opens `/tracks`
- **THEN** the page does not scroll horizontally and the navigation is in a menu button

#### Scenario: Reduced motion
- **GIVEN** the user prefers reduced motion
- **WHEN** the user opens any page
- **THEN** no transition or animation of more than a simple fade runs

### Requirement: Collections navigation
The tracks page SHALL link to the user's collections: Favourites and Playlists, and nothing else. The application
SHALL have no Albums page: `/tracks/albums` is an unknown page.

#### Scenario: Collection links
- **WHEN** the user opens `/tracks`
- **THEN** the collections navigation links to `/tracks/favourites` and `/tracks/playlists`
- **AND** it has no Albums link

#### Scenario: Albums page is gone
- **WHEN** the user opens `/tracks/albums`
- **THEN** the application shows its not-found page
