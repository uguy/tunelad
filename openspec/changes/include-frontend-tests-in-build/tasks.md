# Tasks

## 1. Branch

- [x] 1.1 Create branch `build/frontend-tests-in-build` from an up-to-date `main` (`git switch main && git pull && git switch -c build/frontend-tests-in-build`) and check `git branch --show-current` prints it

## 2. Make the e2e suite pass and CI-ready (commit: `test(ui): fix stale index e2e test and add ci playwright settings`)

- [ ] 2.1 Update `app/e2e/test.js` to assert the h1 "Welcome to Tune Lad !" with a web-first `toHaveText` assertion, and check `cd app && npm run test:e2e` passes all tests in `test.js` and `player.test.js`
- [ ] 2.2 In `app/playwright.config.js`, make `webServer.command` skip `npm run build` when `E2E_PREBUILT` is set, and add the CI settings from design decision 4 (`forbidOnly`, `retries: 1`, `reuseExistingServer: !process.env.CI`, `list` + `html` reporters with `open: 'never'`). Check `npm run test:e2e` still builds and passes, and `npm run build && E2E_PREBUILT=true CI=true npx playwright test` passes without rebuilding
- [ ] 2.3 Check `cd app && npm ls playwright` resolves to a single version shared by `@playwright/test` and `@vitest/browser-playwright`. If it doesn't, align them in `package.json` and check `npm run test:unit -- --run` passes
- [ ] 2.4 Run `cd app && npm run format`, then commit

## 3. Run frontend tests in the Maven build (commit: `build(ui): run frontend unit and e2e tests in the maven build`)

- [ ] 3.1 Add `skipTests` (`false`) and `skipITs` (`${skipTests}`) to `pom.xml` `<properties>`
- [ ] 3.2 Add a `playwright install` execution (`npx` goal, `generate-test-resources`, `playwright install chromium`, `<skip>${skipTests}</skip>`) and a profile activated by `env.CI` that adds `--with-deps`. Check `./mvnw generate-test-resources` logs the Playwright install step
- [ ] 3.3 Add a `webapp unit tests` execution (`npm` goal, `test` phase, `run test:unit -- --run`, `<skip>${skipTests}</skip>`). Check `./mvnw test` logs the vitest run, and that the build fails when the placeholder assertion is temporarily broken
- [ ] 3.4 Add a `webapp e2e tests` execution (`npm` goal, `integration-test` phase, `run test:e2e`, env `E2E_PREBUILT=true`, `<skip>${skipITs}</skip>`). Check `./mvnw verify` logs the Playwright run without a second `vite build`, and that the build fails when an e2e assertion is temporarily broken
- [ ] 3.5 Check the skip flags: `./mvnw verify -DskipITs` runs vitest but not Playwright, and `./mvnw verify -DskipTests` runs neither (nor the Java tests)
- [ ] 3.6 Document in `README.adoc` (Developing > Building) and in the `CLAUDE.md` Commands section that `verify` runs the frontend unit and e2e tests, downloads Chromium on first run, and how `-DskipITs` / `-DskipTests` skip them. Then commit

## 4. CI workflow (commit: `ci(build): cache playwright browsers and upload report on failure`)

- [ ] 4.1 In `.github/workflows/workflow-build.yml`, add `actions/cache` for `~/.cache/ms-playwright` keyed on `hashFiles('app/package-lock.json')` before the Maven step, and an `actions/upload-artifact` step for `app/playwright-report` with `if: failure()`. Check the YAML with `npx --yes yaml-lint .github/workflows/workflow-build.yml` (or an equivalent parser), then commit

## 5. Verify and open the PR

- [ ] 5.1 Run `./mvnw clean verify` and check it passes, with the logs showing the Java tests, the vitest run and the Playwright run
- [ ] 5.2 Leave `README.adoc` Features unchanged (nothing user-visible changes; the build docs were updated in 3.6)
- [ ] 5.3 Push the branch and open a PR to `main` titled `build(ui): run frontend unit and e2e tests in the maven build`. Check the `build` workflow passes, including the Playwright step on the runner

## Workflow follow-up

- Archive the change on the feature branch with `/openspec-archive-change` before merging.
- Squash-merge the PR once checks pass.
