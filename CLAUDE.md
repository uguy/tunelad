# Tune Lad

Playground app: upload an mp3 with a description, list/search tracks, play them in the browser.
Spring Boot (WebFlux, Spring Modulith, R2DBC + PostgreSQL, Liquibase) backend in `src/`, SvelteKit + Tailwind
frontend in `app/`. The point of the repo is learning these tools, so prefer their idiomatic use.

## Commands

- `docker compose up -d` - PostgreSQL (plus Adminer, Keycloak, and the observability stack: OpenTelemetry Collector, Tempo, Loki, Prometheus, Grafana) for running the app locally
- `./mvnw clean verify` - full build: frontend (incl. `bun run lint` and `bun run check`), backend, all tests (Java, vitest in `test`, Playwright e2e in
  `integration-test`). This is the CI gate; run it before opening a PR. `-DskipITs` skips e2e, `-DskipTests` skips all tests
- `cd app && bun run dev` - frontend with live reload
- `cd app && bun run format` - format frontend sources (CI does not do it for you)

Tests use Testcontainers, so Docker must be running.

## Frontend conventions

- Colours come from theme tokens (`bg-surface`, `text-ink`, `bg-accent`, ...) defined in `app/src/app.css`: never raw
  colours or `dark:` pairs. `palette.test.ts` checks contrast in both themes. Details in `app/README.md`.
- Shared state lives in `app/src/lib/*.svelte.ts` stores; pages refetch on live events with `refreshOnLive`.
- Playwright e2e tests mock the API with `page.route`. The README screenshots are retaken with
  `node doc/screenshots.mjs` (UI, needs a build) and `node doc/api-screenshots.mjs` (Scalar and OpenAPI, needs the app running).

## Architecture rules

- Top-level packages under `org.tunelad` are Spring Modulith modules (`track`, `search`, `favourite`, `playlist`, `live`, ...).
  `ModularApplicationTests` verifies module boundaries and fails the build on violations.
- Allowed dependencies are declared in each module's `package-info.java` (`allowedDependencies`),
  and only packages marked `@NamedInterface` (e.g. `track::command`, `track::event`) are exposed.
  Never use another module's `internal` package. Cross-module side effects go through events.
- Stay reactive: no blocking calls on WebFlux request paths.

## Git workflow

- Never commit to `main`. Branch from an up-to-date `main` as `<type>/<short-kebab-name>`
  (e.g. `feat/track-tags`, `chore/deps-cleanup`).
- Commit messages follow Conventional Commits: `<type>(<scope>): <lowercase subject>`.
  Types: feat, fix, docs, refactor, perf, test, build, ci, chore, revert. `!` marks breaking changes.
  Common scopes: `track`, `search`, `api`, `ui`, `deps`, `deps-dev`, `ci`, `openspec`.
- PRs target `main` and are squash-merged, so the **PR title becomes the commit on main**:
  it must be a valid conventional commit (checked by the `workflow-pr-title` workflow, config in
  `commitlint.config.mjs`).
- Push and open the PR only after `./mvnw clean verify` passes locally.

## OpenSpec

Planned changes go through OpenSpec (`openspec/`, skills in `.claude/skills/`):
`/openspec-explore` to think, `/openspec-propose` to plan, `/openspec-apply-change` to implement,
`/openspec-archive-change` to archive on the feature branch before merge.
Workflow rules live in `openspec/config.yaml`.

## Agent skills

### Issue tracker

Issues live in GitHub Issues (`uguy/tunelad`, via the `gh` CLI). See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-label vocabulary (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
