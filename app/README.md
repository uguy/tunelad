# Tune Lad frontend

SvelteKit (static adapter, SPA fallback), Svelte 5 runes, Tailwind CSS 4, bun. The backend serves the build; see the
[project README](../README.adoc) for running the whole application.

## Commands

| Command                      | What it does                                                                     |
| ---------------------------- | -------------------------------------------------------------------------------- |
| `bun run dev`                | dev server with live reload (proxies nothing: run the backend for the API)       |
| `bun run build`              | production build, also copied to `../target/classes/static` for the backend      |
| `bun run preview`            | serves the build on port 4173, as the e2e tests and `doc/screenshots.mjs` use it |
| `bun run lint`               | prettier check and eslint                                                        |
| `bun run check`              | `svelte-check`                                                                   |
| `bun run format`             | prettier write (CI does not do it for you)                                       |
| `bun run test:unit -- --run` | vitest: `client` (browser) and `server` (node) projects                          |
| `bun run test:e2e`           | Playwright; the REST API is mocked with `page.route`                             |

## Structure

- `src/routes`: pages. `tracks` (list, upload, `favourites`, `playlists`, `playlists/[id]`), `account`, `login`, `about`.
- `src/lib`: shared code. Stores are `*.svelte.ts` classes holding `$state` (`player`, `session`, `favourites`,
  `playlists`, `theme`, `live`); `src/lib/ui` holds the small shared components (`Menu`, `EmptyState`, `PageHeader`, `Toast`).
- `e2e`: Playwright tests, one file per feature.

## Theme

Colours are CSS variables in `src/app.css` (`:root` and `:root[data-theme='dark']`) exposed as Tailwind tokens:
`bg-surface`, `bg-canvas`, `text-ink`, `text-ink-muted`, `bg-accent`, `text-accent-ink`, `border-line`, `text-danger`, ...
Write components with tokens, never raw colours or `dark:` pairs, and they follow the theme for free.
`palette.test.ts` checks the contrast of every pair in both themes.

The theme is the `data-theme` attribute of `<html>`. The inline script in `app.html` sets it before the first paint
(no flash), `theme.svelte.ts` keeps it in step with the choice (System, Light, Dark, stored in `localStorage` under
`tunelad.theme`) and with the operating system. Both share one resolve rule, which `theme.test.ts` covers.

## Live updates

`live.svelte.ts` holds the tab's one `EventSource` on `/api/live`. Events carry ids only: pages refetch through the API
with `refreshOnLive(live, ['type'], refresh)` (`liveRefresh.ts`), which also refetches when the connection comes back.
The track list follows `track.*`; favourites and playlists follow `favourite.changed` and `playlist.changed`, which the
server only sends to the account's own connections.

## Playlists

`playlists.svelte.ts` is the API client and the shared list of playlists. Reordering is native HTML5 drag and drop
(`PlaylistTracks.svelte`, with the pure logic in `playlistOrder.ts`) plus Move up and Move down buttons, the keyboard
and touch alternative. The page shows a new order at once and goes back, with a toast, if saving fails.
