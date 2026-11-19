# Design

## Context

See proposal.md (Why) for the motivation. Current state of the build:

- `frontend-maven-plugin` (2.0.2, working directory `app/`) installs Node into `app/node`,
  runs `npm install`, then `npm run format` and `npm run build` in `generate-resources`.
  `npm run build` copies the static site into `target/classes/static`.
- `app/playwright.config.js` starts `npm run build && npm run preview` on port 4173 and runs
  `app/e2e`. `player.test.js` mocks `/api/tracks/search*` and `/api/tracks/*/play` with
  `page.route` and reads its mp3 fixture from `src/test/resources/mp3/`. `test.js` is stale.
- `app/vite.config.ts` defines two vitest projects: `server` (node, `src/**/*.test.js`) and
  `client` (browser mode on Chromium via `@vitest/browser-playwright`,
  `src/**/*.svelte.test.js`). Only one placeholder `server` test exists today.
- CI runs `./mvnw -B -ntp clean verify` on `ubuntu-latest`. Node is not on the runner's path
  before Maven runs; the plugin provides it.

No Java production code, Modulith module, REST endpoint, or reactive path is affected, so the
module-boundary, reactive and OpenAPI rules do not apply here.

## Goals / Non-Goals

**Goals:**
- Frontend tests run as part of the normal Maven lifecycle and fail the build on failure.
- Standard Maven skip flags work as Java developers expect.
- No second frontend build during `verify`.
- `./mvnw clean verify` on a fresh machine or runner needs no manual Playwright setup.

**Non-Goals:**
- Making frontend test results show up as surefire/failsafe XML reports.
- Changing how developers run tests from `app/` (`npm test`, `npm run test:e2e`).

## Decisions

### 1. Run the tests from `frontend-maven-plugin` executions bound to lifecycle phases

Add executions to the existing plugin:

| Execution          | Goal  | Phase              | Command                                  |
|--------------------|-------|--------------------|------------------------------------------|
| playwright install | `npx` | `generate-test-resources` | `playwright install chromium`     |
| webapp unit tests  | `npm` | `test`             | `run test:unit -- --run`                 |
| webapp e2e tests   | `npm` | `integration-test` | `run test:e2e`                           |

The e2e suite runs in `integration-test`, after `package`, which matches what Maven means by
that phase: it exercises the built artifact (the static site) rather than units. The build
fails in that phase, so `verify` still fails.

*Alternatives:* `exec-maven-plugin` calling `npx` duplicates the Node install the frontend
plugin already manages. The plugin's `jest`/`karma` goals target other runners. A separate
Maven profile, opt-in like `-Pe2e`, would bring back the "nobody runs it" problem this change
fixes.

### 2. Skip flags through Maven property interpolation

Declare in `pom.xml` `<properties>`:

```xml
<skipTests>false</skipTests>
<skipITs>${skipTests}</skipITs>
```

- unit tests and browser install: `<skip>${skipTests}</skip>`
- e2e: `<skip>${skipITs}</skip>`

A `-D` user property overrides the pom value, so `-DskipTests` sets both to true through
interpolation, and `-DskipITs` skips only e2e. These are the property names surefire and
failsafe use, so they behave as Java developers expect. Surefire already reads `skipTests`;
declaring its default as `false` changes nothing for it.

### 3. Reuse the `generate-resources` build in Playwright through an env var

`webServer.command` in `playwright.config.js` becomes
`process.env.E2E_PREBUILT ? 'npm run preview' : 'npm run build && npm run preview'`. The Maven
e2e execution sets `E2E_PREBUILT=true` through the plugin's `<environmentVariables>`.
`npm run test:e2e` run on its own still builds first, so it is never stale.

*Alternative:* checking whether `app/build` exists would serve stale output after source
edits outside Maven.

### 4. Playwright settings for CI

When `process.env.CI` is set (GitHub Actions sets it): `forbidOnly: true`, `retries: 1`,
`reuseExistingServer: false`, and reporters `list` plus `html` with `open: 'never'`.
Locally, `reuseExistingServer: true` keeps a running preview usable. Projects stay at
Chromium only. `/test-results` and `/playwright-report` are already in `app/.gitignore`.

### 5. Browser install in the Maven build, OS dependencies only in CI

`npx playwright install chromium` is idempotent and quick once the browser sits in
`~/.cache/ms-playwright`. It uses the `playwright` version pinned in `app/package-lock.json`,
so the browser always matches the test runner. A Maven profile activated by
`<property><name>env.CI</name></property>` adds `--with-deps`, which apt-installs system
libraries with sudo. GitHub runners allow that without a password; a developer machine would
prompt, so local builds don't use it.

The CI workflow adds:
- `actions/cache` on `~/.cache/ms-playwright`, keyed on `hashFiles('app/package-lock.json')`
- `actions/upload-artifact` of `app/playwright-report` with `if: failure()`

### 6. Fix the stale test instead of deleting it

`app/e2e/test.js` asserts the index heading. Update it to "Welcome to Tune Lad !" and switch
to a web-first assertion (`expect(page.getByRole('heading', { level: 1 })).toHaveText(...)`).
It stays as a cheap smoke test that the built site serves.

## Risks / Trade-offs

- [Builds get slower: Chromium download on a cold cache, a preview server, the e2e run] →
  Cache the browser in CI. The suite is small, and the mocked API keeps it fast.
- [`@playwright/test` and the `playwright` used by `@vitest/browser-playwright` resolve to
  different versions, so vitest browser mode can't find its browser] → Today the `client`
  project has no test files, so only `server` tests run. When implementing, check
  `npm ls playwright` resolves to a single version. Dependabot updates them together.
- [Port 4173 is busy locally] → Locally `reuseExistingServer` is true. In CI the runner is
  clean.
- [Flaky media timing in `player.test.js` (it polls `currentTime`)] → One retry in CI.
  The HTML report is uploaded for diagnosis.
- [`--with-deps` apt step fails on a future runner image] → It only runs in CI. Remove the
  profile flag if the runner image already ships the libraries.

## Migration Plan

No deployment impact. Rollback is reverting the PR. Developers who want the old speed can use
`-DskipITs` or `-DskipTests`.
