# Proposal

## Why

`/login` is Spring Security's generated page: bare HTML with plain links, no header, no palette, no Tailwind. It looks
unrelated to the rest of Tune Lad, and it is the first screen a visitor sees when they log in. It cannot be styled
from the SPA because the server renders it.

## What Changes

- `/login` becomes a SvelteKit route, using the root layout (header, footer) and the app's tokens: a `.card` listing
  one `.btn-accent` button per login provider.
- New open endpoint `GET /api/login-providers` returns the configured providers as `[{ id, name }]`, so the page can
  render them.
- The generated Spring page (`LoginPageGeneratingWebFilter` in `SecurityConfig`) is removed. The entry point keeps
  redirecting browser pages to `/login`, which the SPA now serves.
- `SpaWebFilter` serves the SPA for `/login`, so a direct hit or a redirect does not 404.
- The login buttons link to `/oauth2/authorization/{id}` as external links (a full page load): Spring starts the
  redirect, not the SPA router.
- The page shows a short message when no provider is configured.
- No breaking REST API change: one endpoint is added, none is changed or removed.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `account`: the "Log in through an OpenID Connect provider" requirement now says the provider list is available as
  JSON, and that `/login` renders it in the application.

## Impact

- Modulith modules: `account` (new endpoint), `infrastructure` (security config, SPA filter). Touches both the REST
  API and the UI.
- Backend: `SecurityConfig`, `SpaWebFilter`, a new controller in `org.tunelad.account.web`, the OpenAPI description
  (new `GET /api/login-providers` operation).
- Frontend: new `app/src/routes/login/+page.svelte`; `AccountMenu.svelte` keeps its `/login` link.
- Tests: Java endpoint test, vitest for the page, Playwright check in `app/e2e/account.test.js`.
- Suggested PR title: `feat(ui): restyle the login page in the app`

## Out of scope

- Skipping the login page when there is a single provider, or any change to the login flow itself.
- Styling Keycloak's own login screens.
- Provider logos or per-provider branding.
- Local username/password login.
