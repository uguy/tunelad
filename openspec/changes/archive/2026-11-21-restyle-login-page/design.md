# Design

## Context

`/login` is served by Spring Security's `LoginPageGeneratingWebFilter`, registered in `SecurityConfig.loginPage(...)`
with a link per registered client. The SPA has no `/login` route, and `AccountMenu.svelte` reaches the page with
`rel="external"`. `SpaWebFilter` rewrites a fixed list of paths (`/tracks`, `/about`) to `/` so a refresh does not 404.
The entry point redirects non-`/api` requests that need authentication to `/login`. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- The login page is rendered by the SPA with the app's layout and tokens.
- The provider list comes from one backend source of truth, the registered OAuth2 clients.

**Non-Goals:**
- Changing the login flow or the entry point's redirect target.

## Decisions

**Endpoint in the `account` module.** `GET /api/login-providers` is a controller in `org.tunelad.account.web`,
next to `AccountController`, returning `Flux<LoginProvider>` (`id`, `name`) built from the
`ReactiveClientRegistrationRepository`. Login belongs to `account`, which already allows the `infrastructure`
dependency. The repository is Spring's own bean, so no module boundary is crossed.
*Alternative:* put it in `infrastructure.web.security`, next to `SecurityConfig`. Rejected: it is account behaviour,
and the spec lives under `account`.

**Open to anonymous users.** `SecurityConfig` already ends with `anyExchange().permitAll()`, so no rule is needed; a
test pins it so a later tightening cannot silently break the login page.

**Reactive.** The repository is only iterated, as `SecurityConfig.loginPage(...)` does today. It is a
`ReactiveClientRegistrationRepository` that implements `Iterable` only in its in-memory form, so the controller
handles that case the same way and returns an empty list otherwise. No blocking call.

**SPA route.** `app/src/routes/login/+page.svelte` fetches the list in `+page.ts` through `api.ts`, and renders a
`.card` with one `.btn-accent` link per provider to `/oauth2/authorization/{id}`, with `rel="external"` so the router
leaves it to Spring. An empty list shows a short message.

**Remove the generated page.** Delete `loginPage(...)` and its `addFilterAt`. Without it Spring no longer answers
`/login`, so `SpaWebFilter` must rewrite `/login` to `/`, by adding it to `UI_PATH_PREFIXES`.

**OpenAPI.** The description gains one operation, `GET /api/login-providers`, with a 200 response (array of
`LoginProvider`) and no security requirement, annotated like `AccountController`.

**Dev proxy.** `vite.config.ts` proxies `/login` to the backend, for the OAuth callback under it, and `vite preview`
(used by the Playwright tests) inherits that. A `bypass` keeps exactly `/login` in the SPA and sends only
`/login/**` to the backend. `AccountMenu`'s link becomes a router link, since the SPA now owns the route.

**Service worker.** The PWA's navigation denylist matched `^/login`; it narrows to `^/login/`, so `/login` is served
from the precached shell like any SPA route while the OAuth callback still reaches the server. The service-worker
e2e test says the same.

## Risks / Trade-offs

- [`/login` 404s on a direct hit or redirect] -> add it to `SpaWebFilter` and cover it with a Playwright test that
  loads `/login` directly.
- [The SPA filter order versus security's own `/login` handling after the filter is removed] -> nothing else maps
  `/login`; check by hitting it with and without a session in the e2e test.
- [A failure loading providers leaves a blank page] -> the page shows the same short message as the empty case.
- [`/login/oauth2/code/{provider}` shares the prefix] -> `SpaWebFilter` matches the exact path only, so the OAuth
  callback is untouched.

## Migration Plan

No data or configuration migration. The change deploys as one build; rolling back restores the generated page.
