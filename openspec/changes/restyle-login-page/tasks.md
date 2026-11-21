# Tasks

## 1. Branch

- [x] 1.1 Create `feat/login-page` from an up-to-date `main`; verify with `git branch --show-current`

## 2. Provider list endpoint (`feat(account)`)

- [x] 2.1 Add `LoginProvider` (`id`, `name`) and `GET /api/login-providers` in `org.tunelad.account.web`, built from the registered clients with no blocking call, with OpenAPI annotations like `AccountController`; verify the OpenAPI description lists the operation
- [x] 2.2 Add a WebTestClient test: anonymous request returns 200 with the Keycloak provider's `id` and `name`; verify `./mvnw test -Dtest=<the test>` passes
- [x] 2.3 Commit: `feat(account): list the login providers as json`

## 3. Login page in the SPA (`feat(ui)`)

- [x] 3.1 Add `app/src/routes/login/+page.ts` and `+page.svelte`: fetch the providers through `api.ts`, render a `.card` with a `.btn-accent` `rel="external"` link per provider to `/oauth2/authorization/{id}`, and a short message when the list is empty or the fetch fails
- [x] 3.2 Add a vitest test for the page: one button per provider, and the empty message; verify `cd app && npm test` passes
- [x] 3.3 Run `npm run format`, `npm run lint` and `npm run check`; verify they pass
- [x] 3.4 Let the dev and preview proxy in `app/vite.config.ts` leave exactly `/login` to the SPA, while `/login/oauth2/code/...` still goes to the backend, and make `AccountMenu`'s link a router link; verify the vitest and Playwright tests reach `/login`
- [x] 3.5 Commit: `feat(ui): add a login page that matches the app`

## 4. Hand `/login` over to the SPA (`refactor(account)`)

- [x] 4.1 Remove `loginPage(...)` and its `addFilterAt` from `SecurityConfig`; add `/login` to `SpaWebFilter`'s paths; verify the OAuth callback `/login/oauth2/code/{provider}` is not rewritten
- [x] 4.2 Add a Playwright test in `app/e2e/account.test.js`: loading `/login` directly shows the app header and a Keycloak button, and the button starts the Keycloak login; verify `./mvnw verify` runs it green
- [x] 4.3 Update `app/e2e/service-worker.test.js` and the worker's denylist for `/login` now being a SPA route: only `/login/**` (the OAuth callback) is left to the server; verify `npx playwright test` passes
- [x] 4.4 Commit: `refactor(account): serve the login page from the app`

## 5. Wrap-up

- [ ] 5.1 Run `./mvnw clean verify`; verify it passes
- [x] 5.2 Update the README.adoc features list if the login page is mentioned; verify with `grep -n -i login README.adoc`
- [ ] 5.3 Open a PR titled `feat(ui): restyle the login page in the app`, only after 5.1 passes

## Workflow follow-up

- Archive the change on the feature branch before merge, with `/openspec-archive-change`.
