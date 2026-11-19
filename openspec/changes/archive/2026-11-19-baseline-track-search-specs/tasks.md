# Tasks

## 1. Branch and planning artifacts

- [x] 1.1 Create branch `test/baseline-track-search-specs` from an up-to-date `main` (`git switch main && git pull --ff-only && git switch -c test/baseline-track-search-specs`); verify with `git branch --show-current`
- [x] 1.2 Commit this change's planning artifacts (`openspec/changes/baseline-track-search-specs/`) as `docs(openspec): plan baseline specs for track and search`; verify `openspec validate baseline-track-search-specs --strict` passes and `git status` is clean

## 2. Track API characterization tests (`specs/track`)

- [x] 2.1 Replace the empty `src/test/java/org/tunelad/track/TrackTest.java` with `TrackApiTest`: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@Import(TestContainerDefinitions.class)`, a `WebTestClient` bound to `@LocalServerPort`, a `DELETE /api/tracks` call in `@BeforeEach`, and helpers to upload a fixture mp3 with given parts and resolve a track id by title via `GET /api/tracks`; verify the class compiles and an empty smoke test passes with `./mvnw -Dtest=TrackApiTest test`
- [x] 2.2 Add tests for "Upload a track": `upload_with_all_metadata`, `upload_without_optional_metadata`, and `required_part_missing` (one case each for missing title, description and file, also asserting no track is stored); verify they pass with `./mvnw -Dtest=TrackApiTest test`
- [x] 2.3 Add tests for "Assign a unique identifier", "List tracks" and "Get a track by id": `two_uploads_with_the_same_title`, `library_with_tracks`, `empty_library`, `existing_track`, `unknown_id`; verify they pass with `./mvnw -Dtest=TrackApiTest test`
- [x] 2.4 Add tests for "Play a track" (sending `Range: bytes=0-`, asserting 2xx, `audio/mpeg` and byte-for-byte equality with `Knees_Up.mp3`) and "Delete all tracks": `play_an_uploaded_track`, `play_an_unknown_track`, `clear_a_populated_library`; verify they pass with `./mvnw -Dtest=TrackApiTest test`
- [x] 2.5 Check every scenario in `specs/track/spec.md` has exactly one test with the matching name and that no test asserts out-of-scope behaviour (400 without `Range`, `UNSUPPORTED` format, title rules); then commit as `test(track): add characterization tests for the track API`

## 3. Search characterization tests (`specs/search`)

- [x] 3.1 Create `src/test/java/org/tunelad/search/TrackSearchApiTest` with the same setup as `TrackApiTest` (shared Spring context), uploading tracks with explicit titles and descriptions ("Knees Up", "Mossy Banks", a description mentioning "Breabach"); verify an empty smoke test passes with `./mvnw -Dtest=TrackSearchApiTest test`
- [x] 3.2 Add tests for "Search tracks by words in title or description": `match_on_a_title_word`, `match_on_a_description_word`, `case_insensitive_match`, `any_query_word_matches`; verify they pass with `./mvnw -Dtest=TrackSearchApiTest test`
- [x] 3.3 Add tests for "Match whole words, including word variants" and "Order search results by relevance": `word_variant_matches`, `partial_word_does_not_match`, `better_match_first` (data designed per design.md so the scores differ clearly); verify they pass with `./mvnw -Dtest=TrackSearchApiTest test`
- [x] 3.4 Add tests for "Blank query returns every track" and "No match returns an empty result": `empty_query`, `whitespace_only_query`, `unmatched_word`; verify they pass with `./mvnw -Dtest=TrackSearchApiTest test`
- [x] 3.5 Check every scenario in `specs/search/spec.md` has exactly one test with the matching name; then commit as `test(search): add characterization tests for track search`

## 4. Verify and open the PR

- [x] 4.1 Run `./mvnw clean verify` and verify the build succeeds, including `ModularApplicationTests` and both new test classes (no README change: behaviour is unchanged, nothing user-visible)
- [x] 4.2 Archive the change on the branch (`openspec archive baseline-track-search-specs`), verify `openspec/specs/track/spec.md` and `openspec/specs/search/spec.md` exist with their Purpose sections, and commit as `docs(openspec): archive baseline-track-search-specs`
- [x] 4.3 Push the branch and open a PR to `main` titled `test(track): add baseline specs and characterization tests for track and search`; verify the `build` and `commitlint` checks pass

## Workflow follow-up

- Squash-merge the PR once checks pass.
- Open the follow-up changes listed in the proposal's Out of scope section as needed: `fix-range-streaming`, `validate-track-upload`, `wire-track-events`, `track-tags`, `extract-search-module`.
