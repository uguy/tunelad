# Design

## Context

See proposal.md for motivation. Observed state:

- Frontend: SvelteKit (static adapter, SPA fallback served by `SpaWebFilter`) with Tailwind v4. Colours already are
  `@theme` tokens in `app/src/app.css` (`bg-accent`, `text-ink`, `bg-surface`, `bg-canvas`, `border-line`, ...), used
  through `.btn-accent`, `.btn-quiet` and `.card`. The player already has a queue with next and previous
  (`PlayerState.playFrom`), and `/tracks/favourites`, `/tracks/playlists` and `/tracks/albums` are placeholders (albums is deleted by this change).
- Backend: Spring Modulith modules `account`, `track`, `search`, `media`, `live`. One Liquibase SQL changelog per
  module, tables prefixed by the module, and no foreign key across module prefixes. Other modules learn of track
  changes through `track::event` (`NewTrackSaved`, `TrackUpdated`, `TrackDeleted`), consumed with
  `@ApplicationModuleListener` (async, after commit, at-least-once, blocking writes on a task executor thread: see
  `SearchProjection`). The current user comes from `CurrentAccount.get()`.
- `live` already routes events to one account's connections (`LiveSubjects.account`, `Audience`), but only library
  events use it today: `LiveBridge` turns events of `search` and `media` into live events, and the UI's
  `refreshOnLive` helper refetches on them and on reconnect.
- Docs: `README.adoc` has a features list, data model and live sections, and three screenshots in `doc/`;
  `app/README.md` is the untouched `create-svelte` text; Grafana provisions `overview`, `http` and `jvm` dashboards,
  generic over `http_server_requests` by `uri`, and the app exports no custom metric.
- `SecurityConfig` lists authenticated routes explicitly, so new endpoints must be added there.

## Goals / Non-Goals

**Goals:**
- One design language and a dark theme that costs nothing per component: components use semantic tokens, never raw
  colours or `dark:` pairs.
- Favourites and playlists as independent Modulith modules that only know tracks by id.
- No new runtime dependency.

**Non-Goals:**
- Offline playlists, live sync between different users (favourites and playlists are private), touch dragging.
- A design-system package or Storybook: components stay in `app/src/lib`.

## Decisions

### 1. Theme tokens are CSS variables switched by a `data-theme` attribute
`app.css` keeps the `@theme` token names but points them at CSS variables (`--color-surface: var(--surface)`),
declared for light under `:root` and for dark under `:root[data-theme='dark']`. Tailwind utilities written today
(`bg-surface`, `text-ink`) therefore switch with no component change, and `@custom-variant dark` is declared as
`(&:where([data-theme=dark], [data-theme=dark] *))` for the few cases that need an explicit override.
*Alternative:* `dark:` variants on every element: doubles every class list and drifts. *Alternative:* the default
`prefers-color-scheme` variant only: cannot support a manual override.

Dark values keep the brand: the accent stays orange (a lighter `accent-strong` for text on dark surfaces so text-sized
accent still reaches 4.5:1), the header stays charcoal, surfaces are layered charcoal (canvas darkest, cards one
step lighter, hairline `line` borders instead of shadows). Contrast pairs are checked in a vitest test that computes
the ratio of each text/background token pair in both themes, so the 4.5:1 and 3:1 requirements are enforced by the
build.

### 2. Theme state: a tiny store, an inline script, `localStorage`
`theme.svelte.ts` holds `preference` (`'system' | 'light' | 'dark'`, persisted under `tunelad.theme`, absent means
system) and the derived `resolved` theme. It listens to `matchMedia('(prefers-color-scheme: dark)')` while the
preference is `system`. An inline script in `app.html`, run before first paint, reads the same key and sets
`data-theme`, `color-scheme` and the `theme-color` meta tag, so there is no flash. The store only reacts and
updates; the script and the store share the key and the resolve rule, covered by a unit test that loads both.
The switch is a menu button with three radio items (System, Light, Dark): a plain two-state toggle could not return
to "system".
*Alternative:* cookie plus server rendering: the app is a static SPA, so no server render to use it.

### 3. Component structure of the redesign
Shared pieces move into `app/src/lib/ui/` (`Button` styles stay as classes; new `Menu.svelte` (disclosure menu with
keyboard handling and `aria-expanded`), `EmptyState.svelte`, `Toast.svelte`, `PageHeader.svelte`). Pages keep their
routes. `Header` is rebuilt around `Menu` for the account and theme controls. `TrackList` gains the favourite toggle
and "Add to playlist" menu. `PlayerBar` gains the favourite toggle. The `frontend-design` skill guides the visual
pass (type scale, spacing, accent use) and the Svelte MCP's autofixer is run over every new or heavily edited
component.

### 4. `favourite` module
- Table `favourite_favourites (account_id TEXT, track_id TEXT, created_at TIMESTAMPTZ, PRIMARY KEY (account_id,
  track_id))` in `favourite.sql`, registered in the master changelog. `track_id` is a value, not a foreign key.
- API: `PUT` and `DELETE /api/tracks/{id}/favourite`, `GET /api/me/favourites`, in `favourite.web`. They are
  idempotent through `INSERT ... ON CONFLICT DO NOTHING` and a plain `DELETE`.
- Dependencies: `allowedDependencies = { "account", "track", "track::event" }`. Marking checks the track exists with
  `TrackService.findById`. Listing reads the ids, then loads the tracks with a new `TrackService.findAllById(ids)`
  (a public addition to the `track` module's API, returning tracks in unspecified order; the favourite module orders
  them by `created_at desc`). Ids of tracks that no longer exist are ignored, not an error.
- Each mark, unmark and cleanup publishes `FavouriteChanged(accountId, trackId)` in the `favourite::event` named
  interface, from the service's transaction, so it is delivered after commit (see decision 6).
- `TrackDeleted` is consumed with `@ApplicationModuleListener` to delete the rows (idempotent, at-least-once safe).
  This is a blocking write on a task executor thread, as in `SearchProjection`; request paths stay reactive
  (R2DBC repositories returning `Mono` and `Flux`).

### 5. `playlist` module
- Tables `playlist_playlists (id TEXT, owner_id TEXT, name TEXT, description TEXT, created_at, updated_at)` and
  `playlist_items (playlist_id TEXT REFERENCES playlist_playlists ON DELETE CASCADE, track_id TEXT, position INT,
  PRIMARY KEY (playlist_id, track_id))` with a unique `(playlist_id, position)`; the foreign key stays inside the
  module prefix, `track_id` is a value.
- API under `/api/playlists` as in the spec. Ownership is enforced in the service: a playlist of another owner is
  reported as `NotFoundException` (the existing 404 translation), never `ForbiddenException`, so ids do not leak.
- Adding appends at `max(position) + 1` in one statement. Removing closes the gap in the same transaction (via
  `@ReactiveTransactional`). Reordering validates the given ids against the current set, then rewrites positions in
  one transaction; a mismatch is a 400. The same gap-closing runs when a `TrackDeleted` listener removes the track
  from every playlist. Every create, rename, delete, add, remove and reorder, and that cleanup for each playlist
  it touches, publishes `PlaylistChanged(ownerId, playlistId)` in the `playlist::event` named interface.
- Reading a playlist loads item ids in order, then the tracks through `TrackService.findAllById`, and returns them in
  playlist order. `trackCount` for the list comes from a `COUNT` subquery, so the list does not load tracks.
- Dependencies: `{ "account", "track", "track::event" }`.

### 6. Live updates for favourites and playlists
`LiveBridge` listens to `FavouriteChanged` and `PlaylistChanged` (so `live` declares `favourite::event` and
`playlist::event` in `allowedDependencies`) and publishes `favourite.changed` and `playlist.changed` with
`LiveSubjects.account(accountId, type)`, the existing per-account subject, so the existing audience filtering applies:
only that account's connections receive them, on every instance, with the id only in the body. Delivery stays best
effort and swallowed on failure, like the library events. It is the owner-facing module that publishes the event, not
the track module: the bridge never learns about favourites by reading their tables.
*Alternative:* have the browser send a "broadcast channel" message between tabs (`BroadcastChannel`): free, but misses
other devices and other instances, and the live module already solves it.
Metrics: `LiveBus` publishing counts `tunelad.live.events.published{type}`, the SSE controller keeps a
`tunelad.live.connections` gauge (increment on subscribe, decrement on cancel or completion) and counts
`tunelad.live.events.delivered{type}`, through a `MeterRegistry` bean (exported by the existing OTLP pipeline). Tag
values are the fixed event types, never ids, to keep cardinality bounded.

### 7. Drag and drop with the browser's native API
Playlist rows are `draggable` with `dragstart`, `dragover` and `drop` handlers that compute the drop index; a
placeholder line shows where the row will land. The reordered list is applied optimistically and saved with the same
`PUT /api/playlists/{id}/tracks` call as the buttons, rolled back with a toast on failure. The Move up and Move down
buttons stay on every row with accessible names that include the track title: they are the keyboard and touch path,
and `aria-live` announces the new position.
*Alternative:* `svelte-dnd-action` (touch support, keyboard drag) at the cost of a dependency, a Svelte 5 compatibility
check and a second reordering code path to learn. Rejected for a playground with a working button fallback; it can
replace the native handlers later without changing the API.
Native drag and drop does not start on touch screens, which the buttons cover; a Playwright test drives it with
`locator.dragTo`.

### 8. Security and OpenAPI
`SecurityConfig` marks `/api/me/favourites`, `/api/tracks/*/favourite` and `/api/playlists/**` as authenticated, which
answers 401 without redirect like `/api/me`. CSRF applies to the new state-changing routes through the existing
session rules. The OpenAPI description gains tags "Favourites" and "Playlists", the nine new operations with their
request and response schemas (`PlaylistDTO`, `PlaylistSummary`, `PlaylistInput`), and the `bearerAuth` security
requirement; no existing operation or schema changes (`TrackDTO` is untouched), so the change is additive.

### 9. Frontend data flow
`favourites.svelte.ts` holds the set of favourite track ids once a user is known (`session.account`), loaded from
`/api/me/favourites`; toggles update the set optimistically and roll back with a toast on failure. The favourites page
and the account page read the same endpoint. Playlists use `load` functions per route, with a refetch after each
mutation and on `playlist.changed`. The favourites store, the favourites and playlists pages and the account counts
use `refreshOnLive` for `favourite.changed` and `playlist.changed` and on reconnect, so every tab converges on the
server's state; the tab that made a change refetches the same data, which is idempotent. A playlist page whose
playlist is gone shows its not-found message. The player needs no change for playlist playback: the playlist page calls
`player.playFrom(queue, index)`, and the existing end-of-track handler already advances through the queue; a test
pins that it stops after the last track.

### 10. Account page
`/account` reads `session.account` for name and role and fetches the two counts from the favourites and playlists
endpoints. No new backend endpoint: `GET /api/me` stays as is. Anonymous visitors see a login prompt, as on the
other personal pages.

### 11. Docs, screenshots and dashboards are part of the change
- `README.adoc`: features (theme, favourites, playlists with drag and drop, account page, live updates now covering
  favourites and playlists), the data model section (new tables, modules, and events), the live updates section, the
  UI section, and the observability section (new dashboard and metrics).
- Screenshots in `doc/` are retaken from the running app with Playwright against seeded data (the `ImporterTest`
  samples, alice logged in): track list light and dark, favourites, playlist (with drag handles), account, plus the
  existing Scalar and OpenAPI shots, since both gain the new endpoints. A small script under `doc/` regenerates them
  so they can be refreshed, and its README comment says how.
- `app/README.md` is replaced by what the frontend really is: structure, theme tokens, commands, tests.
- `CLAUDE.md` gains the frontend conventions the redesign introduces (semantic colour tokens, never raw colours or
  `dark:` pairs; the shared components; the live helper) and the new modules.
- The PWA manifest `theme_color` and `background_color` are checked against the redesign.
- Grafana: a new provisioned dashboard `live.json` (_Tune Lad - Live and library_): open live connections, live events
  published and delivered per second by type, favourite and playlist API request rates and latency from
  `http_server_requests` filtered on their URIs, with the same `$application` variable and folder as the others. The
  existing three dashboards need no change (they group by `uri`, so the new endpoints appear by themselves); this is
  checked, not assumed.

## Risks / Trade-offs

- [Redesign touches every component, so regressions in untouched behaviour] → keep the existing vitest and Playwright
  suites green throughout, run them after each task group, and keep selectors (roles and accessible names) stable.
- [Dark theme misses a hard-coded colour] → grep gate for raw colour utilities (`bg-white`, `text-gray-*`, hex values)
  in `app/src`, and a Playwright screenshot-free check that no page has a white background in dark mode.
- [Orphaned favourites or playlist items if the `TrackDeleted` listener fails] → the event publication registry
  retries incomplete publications; listing already ignores ids of missing tracks.
- [Position rewrite races between two reorders] → the rewrite runs in one transaction and the unique
  `(playlist_id, position)` makes a losing writer fail, surfaced as 409 Conflict rather than a corrupted order.
- [`findAllById` on `TrackService` widens the track module's public API] → it is read-only, and the alternative (N
  calls to `findById`) costs N queries per list.
- [Native drag and drop looks and behaves slightly differently per browser, and not at all on touch] → the buttons are
  a full alternative, and the drop indicator is plain CSS, not the browser's drag image alone.
- [Event storms: a track in many playlists generates one event per playlist on deletion] → events are tiny, best
  effort, and the UI debounces refetches per event type.
- [Screenshots go stale again] → the regeneration script, and a task to run it when the UI changes.
- [Metric names drift from the dashboard] → a Testcontainers test reads the meters by name, and the dashboard JSON is
  validated by a test that parses it and checks each expression's metric exists in the app's exported names.
- [Large scope in one PR] → tasks are grouped into reviewable commits per concern (theme, redesign, favourites,
  playlists, account), each leaving the build green. If review asks for it, the docs and dashboard group can ship as its own PR on top.

## Migration Plan

Liquibase creates the four new tables on startup; they are additive and unused by old code, so rollback is
reverting the deployment (the tables may stay). There is no data migration. The theme choice lives in the browser; a
user with no stored value gets their system theme, so no one is surprised on first load.
