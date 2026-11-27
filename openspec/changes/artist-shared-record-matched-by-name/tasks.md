# Tasks

## 1. Branch

- [x] 1.1 Create `feat/track-artists` from an up-to-date `main`; verify `git branch --show-current` shows it and that the OpenSpec change files are on it

## 2. Artist storage and matching

- [x] 2.1 Add the Liquibase changeset in `track.sql` (create `track_artists` with a unique index on `lower(name)`, backfill from existing names, add and fill `track_tracks.artist_id`, drop `artist_name`); verify with a Testcontainers test that existing rows migrate to shared Artists
- [x] 2.2 Add the `Artists` port and its `db` implementation (`findOrCreate`, race-safe via `ON CONFLICT`), and map `Track` to `artistId` with the name resolved on read; verify with a Testcontainers test for create, reuse, case and whitespace matching, blank and concurrent first use
- [x] 2.3 Use `findOrCreate` in `DefaultTrackService` on upload and edit, blank clearing the Artist; verify with `TrackApiTest` scenarios from the `track` delta spec; commit as `feat(track): share artists between tracks`

## 3. Search and docs

- [x] 3.1 Add a search API test that a track uploaded as "altan" after "Altan" appears in search results with the artist "Altan"; verify `TrackSearchApiTest` passes
- [x] 3.2 Update `GLOSSARY.md` only if the Artist term changed (matching rule: trimmed, case-insensitive); update the README.adoc features list if user-visible; verify with `git diff`

## 4. Verify and PR

- [x] 4.1 Run `./mvnw clean verify`; verify it passes
- [ ] 4.2 Push and open the PR titled `feat(track): add shared artists`, body ending with `Closes #816`; verify the `workflow-pr-title` check passes
