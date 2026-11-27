# Tasks

## 1. Branch and theme foundation

- [ ] 1.1 From an up-to-date `main`, create the branch `feat/ui-redesign-favourites-playlists`; verify with `git branch --show-current`
- [ ] 1.2 Turn the palette in `app/src/app.css` into light and dark CSS variables under `:root` and `:root[data-theme='dark']`, keep the `@theme` token names, add the `dark` custom variant; verify with `bun run check` and a vitest that computes the contrast ratio of every text/background token pair in both themes (4.5:1 text, 3:1 large text and icons)
- [ ] 1.3 Add `app/src/lib/theme.svelte.ts` (preference `system|light|dark`, persisted under `tunelad.theme`, follows `prefers-color-scheme` while `system`) and the inline pre-paint script in `app/src/app.html` (sets `data-theme`, `color-scheme`, `theme-color`); verify with vitest for the store (stored choice, system change, back to system, script and store resolve the same theme)
- [ ] 1.4 Commit: `feat(ui): add light and dark theme tokens with system default`

## 2. Shared components and app shell redesign

- [ ] 2.1 Build `Menu.svelte` (disclosure menu: `aria-expanded`, Escape and outside click close, arrow keys), `EmptyState.svelte`, `Toast.svelte` and `PageHeader.svelte` in `app/src/lib/ui/`; verify with vitest-browser component tests for keyboard and ARIA behaviour
- [ ] 2.2 Rebuild `Header.svelte` and `AccountMenu.svelte` on `Menu`: nav, theme switch (System, Light, Dark with an accessible name and active state), account menu (account, favourites, playlists, log out), mobile menu; remove the hard-coded colours; verify with Playwright (theme switch persists across reload, system theme default via `colorScheme` emulation, account menu entries, anonymous header) and update `account.test.js` for the menu
- [ ] 2.3 Restyle `Footer`, `+layout`, `PlayerBar`, `Waveform` and the global focus, spacing and reduced-motion rules with the `frontend-design` skill; verify with `bun run lint`, `bun run check`, the existing player and waveform tests, and a Playwright check of no horizontal scroll at 360 px
- [ ] 2.4 Commit: `feat(ui): redesign the app shell, header and player bar`

## 3. Page redesign

- [ ] 3.1 Restyle `TrackList.svelte` and the Tracks page (search, track rows or cards, tags, owner, empty and loading states) keeping accessible names stable; verify with the existing `TrackList`, `Tracks` and e2e tests passing unchanged
- [ ] 3.2 Restyle the home, upload, login and about pages (refresh the About page text to match the app); delete `app/src/routes/tracks/albums/` and the Albums link in `routes/tracks/+page.svelte`; verify with their existing vitest and Playwright tests, a Playwright test that the collections nav has only Favourites and Playlists and `/tracks/albums` shows the not-found page, and a grep gate showing no raw colour utilities (`bg-white`, `text-gray-*`, hex colours) and no `albums` reference left in `app/src`
- [ ] 3.3 Add a Playwright test that opens every page in dark mode and asserts the page background is dark (no light panel), at desktop and 360 px widths
- [ ] 3.4 Run `bun run format`, then commit: `feat(ui): redesign tracks, home, upload, login and about pages`

## 4. Favourites backend

- [ ] 4.1 Add `TrackService.findAllById(Collection<String>)` to the `track` module's API and implementation; verify with a track service test (Testcontainers)
- [ ] 4.2 Create the `favourite` module: `package-info` (`allowedDependencies = { "account", "track", "track::event" }`), `favourite.sql` registered in `db.changelog-master.yaml`, repository, service, `TrackDeleted` listener; verify with Testcontainers tests for marking (idempotent, 404 unknown track), unmarking, listing order (newest first), privacy between users, and cleanup after a track is deleted
- [ ] 4.3 Publish `FavouriteChanged(accountId, trackId)` from the service and the cleanup listener in a `favourite::event` named interface; verify with a Testcontainers test that each mark, unmark and cleanup publishes it once after commit
- [ ] 4.4 Add `FavouriteController` (`PUT` and `DELETE /api/tracks/{id}/favourite`, `GET /api/me/favourites`) with OpenAPI annotations and tag "Favourites", and authenticate the routes in `SecurityConfig`; verify with WebTestClient tests for 204, 401 without redirect, 403 without CSRF for a session and success with a bearer token
- [ ] 4.5 Run `ModularApplicationTests`; verify the module boundaries pass, then commit: `feat(track): add favourite tracks api`

## 5. Favourites UI

- [ ] 5.1 Add `favourites.svelte.ts` (id set loaded once a user is known, optimistic toggle with rollback and toast) and the favourite toggle in `TrackList` and `PlayerBar`; verify with vitest for the store (toggle, rollback on error, anonymous) and component tests
- [ ] 5.2 Build the `/tracks/favourites` page (list, empty state, anonymous prompt) replacing the placeholder; verify with Playwright (API mocked): listed and playable, empty state links to `/tracks`, anonymous login prompt, toggle sends `PUT` then `DELETE`
- [ ] 5.3 Commit: `feat(ui): add favourites page and favourite toggle`

## 6. Playlists backend

- [ ] 6.1 Create the `playlist` module: `package-info`, `playlist.sql` (playlists and ordered items, cascade inside the module only) registered in the master changelog, repositories, service with ownership as `NotFoundException`, position handling in `@ReactiveTransactional`, `TrackDeleted` listener closing gaps; verify with Testcontainers tests for create, validation of the name, list isolation between users, read in order, rename, delete keeping tracks, add (idempotent, unknown track), remove, reorder and wrong-list 400, and a deleted track leaving the order of the others intact
- [ ] 6.2 Publish `PlaylistChanged(ownerId, playlistId)` from every playlist mutation and from the `TrackDeleted` cleanup in a `playlist::event` named interface; verify with a Testcontainers test per operation, including one event per playlist touched by a track deletion
- [ ] 6.3 Add `PlaylistController` for the nine operations in the spec with OpenAPI annotations, tag "Playlists" and the DTOs, and authenticate `/api/playlists/**` in `SecurityConfig`; verify with WebTestClient tests for status codes, 401 without redirect, and CSRF behaviour, and that `/v3/api-docs` lists the new operations
- [ ] 6.4 Run `ModularApplicationTests`, then commit: `feat(api): add playlists api`

## 7. Playlists UI

- [ ] 7.1 Add the playlist API client and the `/tracks/playlists` list page (create, empty state, anonymous prompt); verify with Playwright (API mocked)
- [ ] 7.2 Add the `/tracks/playlists/[id]` page (rename, delete with confirmation, remove track, Move up and Move down buttons with accessible names, native drag and drop of rows with a drop indicator, optimistic reorder with rollback and toast, play all, play from a track, not-found message); verify with Playwright for the move button request, a drag with `locator.dragTo` sending the new order (and no request when dropped in place, and rollback when the save fails), delete, and not found, and a vitest that the player advances through the playlist queue and stops after the last track
- [ ] 7.3 Add the "Add to playlist" menu to `TrackList` (lists the user's playlists, creates one inline, confirms with a toast); verify with Playwright (add request sent, hidden for anonymous visitors)
- [ ] 7.4 Commit: `feat(ui): add playlists pages and add to playlist menu`

## 8. Account page

- [ ] 8.1 Build `/account` (name, role, favourites and playlists counts with links, log out, anonymous prompt); verify with Playwright (member, administrator, counts and links, log out posts `/logout` with the CSRF token, anonymous)
- [ ] 8.2 Commit: `feat(ui): add account details page`

## 9. Live updates and metrics

- [ ] 9.1 Add `favourite.changed` and `playlist.changed` to `LiveBridge` (listening to `favourite::event` and `playlist::event`, publishing on the account subject), add the two event packages to `live`'s `allowedDependencies`; verify with Testcontainers and NATS tests: both tabs of the account receive it, another account and an anonymous client do not, the body holds only `type` and `id`, a track deletion raises one event per affected account, and an upload still succeeds with NATS down
- [ ] 9.2 Add the `tunelad.live.connections` gauge and the `tunelad.live.events.published` and `tunelad.live.events.delivered` counters tagged by event type only; verify with a test that opens two streams and reads the gauge and counters by name from the `MeterRegistry`
- [ ] 9.3 Make the favourites store, favourites page, playlists list, playlist page and account counts refetch on `favourite.changed`, `playlist.changed` and reconnect with `refreshOnLive`; verify with vitest (the store converges after an event) and a Playwright test with two pages of the same user: favourite in one appears in the other, a reorder in one shows in the other, deleting a playlist in one shows not-found in the other
- [ ] 9.4 Run `ModularApplicationTests`, then commit: `feat(live): push favourite and playlist changes to the owner's tabs`

## 10. Docs, screenshots and dashboards

- [ ] 10.1 Add a Grafana dashboard `docker/observability/grafana/provisioning/dashboards/live.json` (_Tune Lad - Live and library_: live connections, events published and delivered by type, favourite and playlist request rate and latency); verify with a test that parses all dashboards, checks the `$application` variable and that every metric used exists in the app's exported names, and by opening it in the local Grafana with the app running
- [ ] 10.2 Check the _Overview_, _HTTP and API_ and _JVM_ dashboards still work with the new endpoints and the live gauge (the `/api/live` stream is a long request: say so in the README if it shows in requests in flight); fix queries that break; verify in local Grafana
- [ ] 10.3 Add a script under `doc/` that runs the app against seeded data (the `ImporterTest` samples, alice logged in) and retakes the screenshots with Playwright; retake `doc/track-list.png` (light) and add `doc/track-list-dark.png`, `doc/favourites.png`, `doc/playlist.png`, `doc/account.png`, and retake `doc/scalar.png` and `doc/api-docs.png` with the Favourites and Playlists endpoints; verify by viewing each image and running the script twice with the same result
- [ ] 10.4 Update `README.adoc`: features (dark theme with system default and switch, favourites, playlists with drag and drop, account page, live updates now covering favourites and playlists, no albums), the data model section (`favourite_favourites`, `playlist_playlists`, `playlist_items`, the new modules and events), the live updates section, the observability section (new dashboard and metrics), the UI section with all screenshots and a note on regenerating them; verify every `image::` path exists, every link resolves, and the text matches the app by reading it against a run
- [ ] 10.5 Replace the `create-svelte` boilerplate in `app/README.md` with the real frontend notes (structure, theme tokens, shared components, live helper, commands, tests), and add the frontend conventions and new modules to `CLAUDE.md`; verify they match `package.json` scripts and the code
- [ ] 10.6 Check the PWA manifest `theme_color` and `background_color` and `app.html` meta tags against the redesigned palette and the installed-app look in both themes; verify with `bun run build` and the service worker e2e test passing
- [ ] 10.7 Commit: `docs: align readme, screenshots and grafana dashboards with the redesign`

## 11. Integration and delivery

- [ ] 11.1 Run the Svelte MCP autofixer over every new or heavily edited component and fix findings; verify `bun run lint`, `bun run check` and `bun run format` are clean
- [ ] 11.2 Start the app (`docker compose up -d`, backend and `bun run dev`) and check the flow by hand in light and dark, with two tabs open: log in, favourite, create a playlist, drag to reorder, play it through, account page, theme switch and reload, and watch the changes appear in the other tab and in the Grafana dashboards
- [ ] 11.3 Run `./mvnw clean verify` and verify it passes (frontend lint and check, Java, vitest and Playwright e2e)
- [ ] 11.4 Archive the change on the feature branch with `/openspec-archive-change`, then push and open a PR titled `feat(ui): redesign the app with dark theme, favourites, playlists and account page`

## Workflow follow-up

- Squash-merge after review; the PR title becomes the commit on `main`.
