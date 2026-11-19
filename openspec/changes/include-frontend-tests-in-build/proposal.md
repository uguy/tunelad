# Proposal

## Why

`./mvnw clean verify` is the CI gate, but it only builds the frontend: the Playwright e2e suite
in `app/e2e` and the vitest unit tests in `app/src` never run in it. A UI regression can be
merged without any check failing. The e2e suite already has a stale test (`app/e2e/test.js`
still expects the "Welcome to SvelteKit" heading), which shows nobody runs it. Now that
`player.test.js` covers real UI requirements (start playback at a position, copy a link), it
needs to gate merges.

## What Changes

- The Maven build runs the frontend vitest unit tests in the `test` phase, next to the Java
  unit tests.
- The Maven build runs the Playwright e2e suite in the `integration-test` phase, against the
  static build served by `vite preview` with the REST API mocked by `page.route`, as the suite
  already does. The suite does not need the backend or MongoDB.
- The Maven build installs the Playwright Chromium browser, so `./mvnw clean verify` works on
  a fresh machine and in GitHub Actions without manual setup.
- When Maven runs Playwright, it reuses the frontend build from `generate-resources` instead of
  building again. `npm run test:e2e` run on its own still builds first.
- The usual Maven flags skip frontend tests: `-DskipTests` skips both suites and
  `-DskipITs` skips e2e only. A failing frontend test fails the build.
- Fix the stale index-page e2e test so the suite passes.
- Playwright uses CI-friendly settings when `CI` is set: `forbidOnly`, one retry, and an HTML
  report kept in `app/playwright-report` that the CI workflow uploads when the build fails.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This is a build and tooling change: no behaviour changes through the REST API or the UI,
so the change sets `skip_specs: true`.

## Impact

- **Modulith modules**: none. No Java production code changes.
- **REST API / UI**: neither changes. No OpenAPI change, no breaking change.
- **Build**: `pom.xml` (new `frontend-maven-plugin` executions), `app/playwright.config.js`,
  `app/e2e/test.js`, and maybe `app/package.json` scripts.
- **CI**: `.github/workflows/workflow-build.yml` caches the Playwright browser and uploads the
  report when the build fails. The build gets slower: browser install on a cold cache, plus
  the e2e run.
- **Local dev**: `./mvnw clean verify` now downloads Chromium (around 150 MB, cached in
  `~/.cache/ms-playwright`) the first time it runs.

## Out of scope

- Full-stack e2e tests against a running Spring Boot app with MongoDB.
- Writing new UI tests beyond fixing the stale one, and replacing the placeholder vitest test.
- Browsers other than Chromium.
- Running lint (`npm run lint`) or `svelte-check` in the build.

## PR title

`build(ui): run frontend unit and e2e tests in the maven build`
