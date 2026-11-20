# Proposal

## Why

The UI is a stock Tailwind layout with the browser's native `<audio controls>` rendered under the page content, so
the player scrolls away, cannot move between tracks and shows nothing of the music itself. A music-streaming style
redesign (persistent bottom player bar, track rows with a large play button and a real, clickable waveform) makes
the app feel like a music app and is a good exercise in Svelte 5 state, Tailwind v4 theming, and reactive
processing in the backend. The look is inspired by mainstream streaming sites but uses Tune Lad's own name, logo
and palette, with no copied logos or assets.

## What Changes

- **New waveform data (media module, REST API)**: after a track's audio is stored, the backend decodes it with
  ffmpeg and computes a fixed number of normalised peak values (200, from 0 to 1). It stores them and serves them on
  a new `GET /api/tracks/{id}/waveform` endpoint, which also says whether the waveform is still pending or could not
  be computed. Tracks uploaded before this change get their waveforms backfilled when the app starts. Deleting a
  track deletes its waveform. The endpoint is additive and nothing in the existing API breaks.
- **New persistent player bar (UI)**: a bar fixed to the bottom of the screen replaces the native audio controls.
  It has play/pause, previous/next through the track list the track was started from, a seekable progress bar with
  elapsed time and duration, volume and mute, and the title and owner of the current track. The existing "copy link
  at current time" control moves into it. The bar stays visible and keeps playing while the user moves between
  pages.
- **Redesigned track list (UI)**: each track becomes a row with an artwork tile generated from the track's id, a large
  round play/pause button that reflects the player's state, the owner and title, tag pills, and the track's waveform.
  Clicking the waveform seeks within the track, or starts it at that point. The edit and delete actions and the
  description stay available on each row.
- **Restyled app (UI)**: a new theme (light background, dark header, orange accent) defined once as Tailwind v4
  theme tokens and applied to the header, home, about, tracks, upload and the placeholder pages (favourites,
  playlists, albums).
- **Tooling**: ffmpeg becomes a prerequisite for running the backend and its tests. The README documents it.

## Capabilities

### New Capabilities

None. The new behaviour belongs to existing modules.

### Modified Capabilities

- `media`: adds the waveform endpoint, computing waveforms after upload, backfilling them, and deleting them with
  their track. Also adds the player bar's observable behaviour: persistence across pages, play/pause,
  previous/next, seeking (progress bar and waveform), volume, and the current track's details. The existing
  requirements (byte ranges, start-time links, copy link) are unchanged and must keep passing.

## Impact

- **Modulith modules**: `media` only on the backend. It gets a waveform service, storage and endpoint, all inside
  the module. `track` is unchanged: it already calls `MediaStore.store` and `MediaStore.delete`, and the media module
  hooks waveform work onto those calls. No new cross-module dependency.
- **REST API / OpenAPI**: adds `GET /api/tracks/{id}/waveform` (tag "Media") and a `Waveform` schema. Nothing
  breaks.
- **UI**: `app/src/app.css` (theme), `+layout.svelte`, `Header.svelte`, `Footer.svelte`, `player.svelte.ts` (grows
  into the player's state: queue, playing, time, volume), `Player.svelte` (replaced by a player bar), `TrackList.svelte`,
  a new `Waveform.svelte`, and the home, about, tracks, upload and placeholder pages. The Playwright tests that use
  the old `<details>` markup (`summary span.px-2`) are updated to the new row markup.
- **Dependencies / environment**: ffmpeg binary on developer machines and CI (`ubuntu-latest` runners ship it).
  No new Maven or npm dependency.
- **Data**: a new MongoDB collection for waveforms, owned by the media module.

## Out of scope

- Shipping ffmpeg inside the OCI image built by `spring-boot:build-image`. Without ffmpeg the app still works and
  shows flat placeholder waveforms. Packaging it is a follow-up.
- Track artwork upload, likes/reposts/comments, timed comments on the waveform, follower counts, or any other social
  feature of streaming sites.
- Real favourites, playlists and albums. Those pages stay placeholders and only get restyled.
- Dark mode, keyboard shortcuts beyond the native focus and activation of the controls, and continuing playback
  across full page reloads.
- Changing track metadata (duration is taken from the browser's audio element, not stored).

## PR title

`feat(ui): redesign the ui with a waveform player`
