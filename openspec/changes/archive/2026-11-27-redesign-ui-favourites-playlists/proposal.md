# Proposal

## Why

The UI works but looks like a first draft: a flat header, plain white cards, no dark mode, and three
"coming soon" placeholders (`/tracks/favourites`, `/tracks/playlists`, `/tracks/albums`). Albums has no plan behind it, so its page and link go instead of being polished. Logged-in users
cannot keep the tracks they like, group tracks into playlists, or see what their account is. This change gives the
app a consistent, polished look in the existing SoundCloud-inspired orange and charcoal palette, with a dark theme,
and delivers the favourites, playlists and account pages the navigation already promises.

## What Changes

- **Redesign of every page and shared component** (header, home, tracks, track list, upload, login, footer,
  player bar) on one design language: same orange accent and charcoal header, refined typography, spacing, cards,
  focus states and motion (respecting `prefers-reduced-motion`).
- **Dark theme.** Colours become theme tokens with a light and a dark value. The theme follows the operating
  system (`prefers-color-scheme`) by default; a switch in the header lets the user force light or dark, and the choice
  is remembered in the browser. No flash of the wrong theme on load.
- **Albums placeholder removed**: the `/tracks/albums` page and its link in the collections navigation are deleted.
- **Account details page** at `/account`, reached from the header's account menu: display name, role (administrator
  or not), and counts of the user's favourites and playlists, with a log out control.
- **Favourites.** A logged-in user can mark any track as a favourite from the track list and the player bar, and
  sees them on `/tracks/favourites` (replacing the placeholder). New `favourite` Modulith module, REST API under
  `/api/me/favourites` and `/api/tracks/{id}/favourite`, Liquibase changelog.
- **Playlists.** A logged-in user can create, rename, describe and delete playlists, add and remove tracks, reorder
  them (by drag and drop, with keyboard-accessible move up and down buttons as the alternative), and play a playlist
  from the start with the player moving on to the next track. Playlists live on
  `/tracks/playlists` (list, replacing the placeholder) and `/tracks/playlists/{id}` (detail). New `playlist`
  Modulith module, REST API under `/api/playlists`, Liquibase changelog.
- **Live updates across tabs.** Favourites and playlists changes are announced to every tab and device of the
  owning account through the existing live module (NATS and Server-Sent Events): new account-addressed event types
  `favourite.changed` and `playlist.changed`, which carry ids only. The `live` module gains them; the `favourite` and
  `playlist` modules publish `favourite::event` and `playlist::event` events.
- **Docs, screenshots and dashboards aligned.** `README.adoc` (features, data model, live updates, UI section and
  all screenshots), `app/README.md`, `CLAUDE.md`, the PWA manifest colours and the Grafana
  dashboards are updated to match: the screenshots are retaken from the redesigned app (light and dark, favourites,
  playlists, account, Scalar and OpenAPI with the new endpoints), and a new Grafana dashboard shows live connections
  and library activity from new Micrometer metrics.
- Playlists are **private to their owner** and favourites are per account; both disappear with the track
  (`TrackDeleted` event), never with a failed cross-module call.
- No breaking REST API change: all additions. `GET /api/me` is unchanged.

Proposed PR title: `feat(ui): redesign the app with dark theme, favourites, playlists and account page`

## Capabilities

### New Capabilities
- `appearance`: the application's visual theme and layout: light and dark themes, system theme by default, the
  theme switch and its persistence, responsive navigation, and accessible contrast and focus in both themes.
- `favourite`: a user's favourite tracks: marking and unmarking, listing them, and cleanup when a track is deleted.
- `playlist`: a user's private ordered playlists of tracks: management, track membership and order, and playing a
  playlist through to its end.

### Modified Capabilities
- `live`: adds the account-addressed event types `favourite.changed` and `playlist.changed`, and the UI refetching
  on them.
- `account`: adds the account details page and the account menu link to it (the header requirement changes from a
  bare name and log out control to a menu).

## Impact

- **Modulith modules:** new `favourite` and `playlist` (each allowed to depend on `account`, `track` and
  `track::event`); `live` may also depend on `favourite::event` and `playlist::event`; `account` gains no new
  dependencies. UI and REST API both change.
- **Backend:** new packages `org.tunelad.favourite` and `org.tunelad.playlist` (web, internal, `package-info`),
  `favourite.sql` and `playlist.sql` Liquibase changelogs registered in `db.changelog-master.yaml`, OpenAPI tags
  for the new endpoints.
- **Frontend:** `app/src/app.css` (theme tokens), `app/src/app.html` (no-flash theme script, `theme-color`),
  every component in `app/src/lib` and every route, new routes `account` and `tracks/playlists/[id]`, new stores
  for theme and favourites, a play queue in the player. Tests: vitest component tests, Playwright e2e for the new
  flows.
- **Docs and ops:** `README.adoc`, `doc/*.png`, `app/README.md`, `CLAUDE.md`, PWA manifest colours,
  `docker/observability/grafana/provisioning/dashboards/` (new `live.json`).
- **Dependencies:** none new. Tailwind v4 `@custom-variant` handles dark mode, and drag and drop uses the browser's
  native API.

## Out of scope

- An albums feature: the placeholder is removed and nothing replaces it.
- Public or shared playlists, collaborative playlists, playlist cover art upload.
- Editing the display name or other profile data (it comes from the identity provider), avatars, account deletion.
- Live updates to other users: favourites and playlists are private, so events only reach the owner's own account.
- Shuffle, repeat modes, and touch-screen dragging (native drag and drop barely works on touch: touch users use the
  move up and down buttons).
- Per-user theme stored on the server; the theme choice stays in the browser.
- Changes to authentication, search ranking or the media pipeline.
- Alerting rules or SLOs on the new metrics; the dashboard only shows them.
