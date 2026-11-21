# Tasks

## 1. Branch

- [x] 1.1 Create `feat/login-page` from an up-to-date `main`; verify with `git branch --show-current`

## 2. Provider list endpoint (`feat(account)`)

- [x] 2.1 Add `LoginProvider` (`id`, `name`) and `GET /api/login-providers` in `org.tunelad.account.web`, built from the registered clients with no blocking call, with OpenAPI annotations like `AccountController`; verify the OpenAPI description lists the operation
- [x] 2.2 Add a WebTestClient test: anonymous request returns 200 with the Keycloak provider's `id` and `name`; verify `./mvnw test -Dtest=<the test>` passes
- [x] 2.3 Commit: `feat(account): list the login providers as json`

## 3. Login page in the SPA (`feat(ui)`)

- [ ] 3.1 Add `app/src/routes/login/+page.ts` and `+page.svelte`: fetch the providers through `api.ts`, render a `.card` with a `.btn-accent` `rel="external"` link per provider to `/oauth2/authorization/{id}`, and a short message when the list is empty or the fetch fails
- [ ] 3.2 Add a vitest test for the page: one button per provider, and the empty message; verify `cd app && npm test` passes
- [ ] 3.3 Run `npm run format`, `npm run lint` and `npm run check`; verify they pass
- [ ] 3.4 Commit: `feat(ui): add a login page that matches the app`

## 4. Hand `/login` over to the SPA (`refactor(account)`)

- [ ] 4.1 Remove `loginPage(...)` and its `addFilterAt` from `SecurityConfig`; add `/login` to `SpaWebFilter`'s paths; verify the OAuth callback `/login/oauth2/code/{provider}` is not rewritten
- [ ] 4.2 Add a Playwright test in `app/e2e/account.test.js`: loading `/login` directly shows the app header and a Keycloak button, and the button starts the Keycloak login; verify `./mvnw verify` runs it green
- [ ] 4.3 Commit: `refactor(account): serve the login page from the app`

## 5. Wrap-up

- [ ] 5.1 Run `./mvnw clean verify`; verify it passes
- [ ] 5.2 Update the README.adoc features list if the login page is mentioned; verify with `grep -n -i login README.adoc`
- [ ] 5.3 Open a PR titled `feat(ui): restyle the login page in the app`, only after 5.1 passes

## Workflow follow-up

- Archive the change on the feature branch before merge, with `/openspec-archive-change`.
