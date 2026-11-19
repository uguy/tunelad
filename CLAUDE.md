# Tune Lad

Playground app: upload an mp3 with a description, list/search tracks, play them in the browser.
Spring Boot (WebFlux, Spring Modulith, MongoDB + GridFS) backend in `src/`, SvelteKit + Tailwind
frontend in `app/`. The point of the repo is learning these tools, so prefer their idiomatic use.

## Commands

- `docker compose up -d` - MongoDB (plus Mongo Express, Zipkin, Prometheus) for running the app locally
- `./mvnw clean verify` - full build: frontend, backend, all tests. This is the CI gate; run it before opening a PR
- `cd app && npm run dev` - frontend with live reload
- `cd app && npm run format` - format frontend sources (CI does not do it for you)

Tests use Testcontainers, so Docker must be running.

## Architecture rules

- Top-level packages under `org.tunelad` are Spring Modulith modules (`track`, `search`, ...).
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
