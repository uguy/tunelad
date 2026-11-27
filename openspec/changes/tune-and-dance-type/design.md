## Context

New module `tune`, same shape as `favourite`/`track`: public API package, `internal`, `web`, an `event` package.
No other module depends on it yet (Sets will, in a later slice).

## Decisions

- **Identity**: Tune id is a generated string like Track ids. thesession.org id is a nullable, unique `source_id` column;
  uniqueness makes import idempotent (concurrent imports resolve via the unique constraint, then re-read).
  `GLOSSARY.md` says a Tune is "Identified by its thesession.org id": reword to "Imported from thesession.org by id, which it keeps; an Account can
  create one by hand when no source matches" (task in tasks.md).
- **Dance type**: a Java enum persisted as text with a CHECK constraint. Fixed list, no table: no admin UI for it in this slice. Exposed through `/api/dance-types`.
- **Aliases**: a `TEXT[]` column (R2DBC PostgreSQL maps it to `String[]`); `q` filter uses `ILIKE` on name or `EXISTS (SELECT 1 FROM unnest(aliases) a WHERE a ILIKE ...)`. Wildcards in `q` are escaped.
- **thesession.org client**: `WebClient` (reactive, non-blocking) in `tune/internal`, behind a `TuneSource` interface so tests substitute a stub; base URL in `tunelad.tune.thesession-url`. Calls `GET /tunes/{id}?format=json`, takes the first setting's `abc` and `key` as the Reference transcription and usual key; 404 maps to not found, timeouts and 5xx to 502. Timeout set on the client.
- **Auth**: endpoints need an authenticated Account like other APIs; the creating Account id is stored (`created_by`, by value, no cross-module FK) for the later ownership slice but not exposed.
- **Events**: none published in this slice (Live updates come in #824).
- **Modulith**: `package-info.java` allows `infrastructure` and `account`. Liquibase `tune.sql` with `tune_` prefixed tables, added to the master changelog.
- **Reactive**: all repository and HTTP calls are `Mono`/`Flux`; nothing blocks.
- **OpenAPI**: new `Tunes` tag with `GET/POST /api/tunes`, `GET /api/tunes/{id}`, `POST /api/tunes/import`, `GET /api/dance-types`, documented with `@Operation`/`@ApiResponse` as in `FavouriteController`. Additions only.
- **UI**: routes `tunes`, `tunes/[id]`, `tunes/new`; a `tunes.svelte.ts` store is unnecessary (no shared state yet), pages load via `+page.ts`. Transcription viewer: `abcjs` (`renderAbc` and `synth`) in a lazy `AbcViewer.svelte`, imported with `await import('abcjs')` only when a Tune with a
  transcription is opened, so the initial bundle is unchanged. A labelled two-option control (sheet / ABC text) switches views; the sheet SVG gets a text
  alternative (the ABC in a visually-hidden element, `aria-label` on the container).
- **Playback**: abcjs `synth.CreateSynth`; the Play button is the user gesture that starts the AudioContext. Starting pauses the shared `player.svelte.ts`
  store so two audio sources never overlap. Stop and a tempo control (warp) are provided; failures show an inline error.
- **Soundfont**: one instrument (flute, octaves 3 to 7, one mp3 per note, about 6 MB in `static/soundfonts/flute-mp3`) served from the app's static files rather than abcjs's default CDN; abcjs fetches only the notes a tune uses, and the service worker does not precache them, so playback works offline and
  with the PWA and adds no external runtime dependency.

## Risks / Trade-offs

- abcjs is large (~500 kB minified): accepted because it is lazy-loaded and absent from the other pages.
- Real audio is not testable in CI: vitest mocks abcjs, Playwright checks the view switch, the SVG and the Play/Stop state only.
- thesession.org JSON shape may change or rate limit: isolated behind `TuneSource`, failures surface as 502.
- Fixed Dance type enum needs a code change to extend; acceptable for a closed traditional vocabulary.
