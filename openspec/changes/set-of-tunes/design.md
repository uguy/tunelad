## Context

Follows the shape of the `tune` module (service in `internal`, controller in `web`, `DatabaseClient` repository, Liquibase changelog per module).
`set` depends on `tune`, which until now exposed only its DTOs: `TuneService` lives in `tune.internal`, which other modules must not use.

## Decisions

- **Uniqueness by key**: `set_sets.tunes_key` holds the ordered tune ids joined by `,` under a unique index. Create is
  `INSERT ... ON CONFLICT (tunes_key) DO NOTHING` then select by key; rows updated tells 201 from 200, and the racing loser's insert does nothing, so
  it reads the winner's row (same pattern as `DbArtists`/tune import). The list itself is stored in `set_set_tunes (set_id, position, tune_id)`,
  primary key `(set_id, position)`, a foreign key to `set_sets` inside the module, and `tune_id` by value (no foreign key across modules). Both inserts run in one
  transaction (`@ReactiveTransactional`); the items are inserted only when the set row was inserted.
- **Tune API**: `org.tunelad.tune.TuneCatalog` (public interface, `findAllById(Collection<String>)` returning `TuneDTO`s), implemented by `TuneService`.
  Marked as the module's API in `tune`'s package; `set`'s `package-info.java` allows `tune`. Set validates that all ids exist (400 otherwise) and
  reads names and Dance types through it when it returns a Set, so Tune renames show in Sets without copies.
- **Limits**: 1 to 20 Tunes, validated in the service (`BadRequestException`); the same Tune may repeat.
- **Browse**: list reads sets, their items, then the Tunes through `TuneCatalog` in two queries (no N+1). Ordering by the joined names is done in memory after loading: the
  collection is small and names live in another module. `tuneId` filter is a SQL `EXISTS` on `set_set_tunes`.
- **Auth**: `POST /api/sets` authenticated in `SecurityConfig`; reads are public. `created_by` (account id by value) is stored, not exposed.
- **Events**: none (live updates are #824).
- **Reactive**: nothing blocks; no external calls.
- **OpenAPI**: new `Sets` tag: `POST /api/sets` (201/200/400/401/403), `GET /api/sets`, `GET /api/sets/{id}` (404). Additions only.
- **UI**: routes `sets`, `sets/new`, `sets/[id]` (`prerender = false` like tunes). The builder uses the tunes search endpoint, a plain list with
  up/down/remove buttons (accessible, no drag and drop needed), and an `aria-live` region announcing changes. A `Set` response to the builder is
  200 or 201; the builder passes the status to the Set page in navigation state (`page.state`) to show "This set already existed".

## Risks / Trade-offs

- A key built from ids is long for 20 Tunes (about 740 chars at 36 per id): fine for a btree index, a hash would not make it clearer.
- Sorting in memory limits scale; acceptable until Sets are numerous, revisit with search.
