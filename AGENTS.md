# Tune Lad

Pet project to learn Spring Modulith, WebFlux, R2DBC and SvelteKit: a collection of Irish music recordings annotated
with their tunes. Prefer idiomatic use of these tools over shortcuts.

## Map

- `src/main/java/org/tunelad/<module>` - backend, one Spring Modulith module per package (`track`, `tune`, `set`, `search`, `favourite`, `playlist`, `comment`, `account`, `live`, `media`, `identification`, `infrastructure`)
- `src/main/resources/db/changelog` - Liquibase changelogs
- `app/` - SvelteKit + Tailwind frontend; shared state in `app/src/lib/*.svelte.ts`
- `sidecar/identification` - separate Node service for audio identification
- `openspec/specs/<module>` - current behaviour per module; `openspec/changes` - changes in flight
- `GLOSSARY.md`, `docs/adr/` - domain language and decisions

## Commands

- `docker compose up -d` - PostgreSQL, Keycloak and the observability stack for running locally; Docker must also run for tests (Testcontainers)
- `./mvnw clean verify` - the CI gate (frontend lint + check, Java tests, vitest, Playwright e2e). `-DskipITs` skips e2e, `-DskipTests` skips all tests
- `cd app && bun run dev` - frontend with live reload; `bun run format` before committing (CI only checks)
- `node doc/screenshots.mjs` (needs a build) and `node doc/api-screenshots.mjs` (needs the app running) retake the README screenshots

## Domain language

Read `GLOSSARY.md` before naming a domain concept in code, tests, specs, issues or PRs, and use its terms verbatim (it lists terms to avoid).
Read the `docs/adr/` entries for the area you are changing; surface any contradiction with an ADR instead of overriding it.

## Backend rules

- Allowed module dependencies live in each module's `package-info.java` (`allowedDependencies`). Only `@NamedInterface` packages (`track::command`, `track::event`, ...) are visible to other modules. `ModularApplicationTests` fails the build on violations.
- Cross-module side effects go through events.
- Stay reactive: no blocking call on a WebFlux request path.
- Every behaviour change ships with a test; REST changes update the OpenAPI description.

## Frontend rules

- Colours come from theme tokens (`bg-surface`, `text-ink`, `bg-accent`, ...) in `app/src/app.css`; raw colours and `dark:` pairs break `palette.test.ts`. See `app/README.md`.
- Pages refetch on live events with `refreshOnLive`.
- Playwright e2e tests mock the API with `page.route`.
- Keep the UI package thin, the UX optimal and accessibility best practices followed.
- Use the Svelte MCP server (`svelte-autofixer`) when writing Svelte components.

## Planned changes (OpenSpec)

Features go through OpenSpec: `/next-slice` picks a roadmap issue, then `/openspec-propose`, `/openspec-apply-change`, `/openspec-archive-change` (archive on the feature branch before merge). `/openspec-explore` is for thinking first. Rules live in `openspec/config.yaml`.

## Git workflow

- Work on `<type>/<short-kebab-name>` branched from an up-to-date `main`; `main` stays untouched.
- Commits and the PR title are Conventional Commits: `<type>(<scope>): <lowercase subject>`. Types: feat, fix, docs, refactor, perf, test, build, ci, chore, revert; `!` marks breaking changes. Scopes: module names, `api`, `ui`, `deps`, `deps-dev`, `ci`, `openspec`. Config in `commitlint.config.mjs`.
- PRs target `main` and are squash-merged, so the PR title becomes the commit on `main`.
- Push and open the PR once `./mvnw clean verify` passes, unless the change touches no code.

## Issues

GitHub Issues on `uguy/tunelad` via `gh`: see `docs/agents/issue-tracker.md` and `docs/agents/triage-labels.md`.

## Model choice

Match the model to the task; escalate when a cheaper one stalls or gets it wrong.

- **Haiku** - lookups and mechanical edits: finding files or usages, reading logs, archiving or syncing OpenSpec changes, renames, formatting, dependency bumps.
- **Sonnet** - the default for well-specified work: implementing an OpenSpec task group, writing tests, small bug fixes, PR bodies, verifying a change.
- **Opus** - judgement-heavy work: `/openspec-propose`, `/openspec-explore`, `/openspec-update-change`, module boundaries and event design, ADRs, hard bug diagnosis, code review.

Delegate to subagents with an explicit `model` (`Explore` on haiku for searches; `general-purpose` on sonnet for implementation).
