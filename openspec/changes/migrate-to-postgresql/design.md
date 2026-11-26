# Design

## Context

See proposal.md for motivation. Today each module owns Mongo collections, `media` stores audio in GridFS, `search`
keeps a text-indexed read model fed by track events, and two Mongo transaction managers exist: a reactive one
(`@ReactiveTransactional`) and a blocking primary one used by the Modulith Event Publication Registry and
`@ApplicationModuleListener`. `TransactionalEvents` publishes events on `boundedElastic` because the registry blocks.
The REST API and its observable behaviour (see `openspec/specs/*`) must not change.

## Goals / Non-Goals

**Goals:** all data in PostgreSQL; reactive access via R2DBC; Liquibase-managed schema; module data stays isolated.

**Non-Goals:** data migration, JSONB, changing the event-driven module design, new features.

## Decisions

### 1. R2DBC for the app, JDBC only where unavoidable
Spring Data R2DBC (with `DatabaseClient` for hand-written SQL) serves all repositories. The Modulith registry
(`spring-modulith-starter-jdbc`) and Liquibase require JDBC, so a small JDBC `DataSource` exists next to the R2DBC
`ConnectionFactory`. Blocking calls: registry writes (already isolated on `boundedElastic` by `TransactionalEvents`)
and Liquibase (runs once at startup, before requests are served).
*Alternative:* JDBC/JPA everywhere, rejected: breaks the "stay reactive" rule and the learning goal.

### 2. Transaction managers
Spring Boot configures no JDBC `DataSource` once an R2DBC `ConnectionFactory` exists, and would leave two transaction
managers ambiguous, so one `PersistenceConfig` in the root package declares the JDBC pool, both managers and lets a
Testcontainers `JdbcConnectionDetails` override the properties. It sits in the root package, not in `infrastructure`,
so that Modulith module tests (which load a single module) get it too.
`R2dbcTransactionManager` keeps the bean name `reactiveTransactionManager` (so `@ReactiveTransactional` and its
Javadoc just change from "MongoDB" to "R2DBC"); the blocking `DataSourceTransactionManager` is `@Primary` for the
registry and `@ApplicationModuleListener`. Replica-set requirements disappear. The registry write still happens on its
own JDBC transaction, not inside the reactive one, which matches today's behaviour (Mongo had the same split).

### 3. One schema, module-prefixed tables, no cross-module FK
```
track_tracks     account_accounts     search_tracks
media_files <-FK- media_chunks        media_waveforms -(FK)-> media_files
event_publication (Modulith registry)
```
Foreign keys exist only inside a module (`media_chunks` and `media_waveforms` to `media_files`, `ON DELETE
CASCADE`). `track_tracks.owner_id` and the track-to-audio key are plain values; cleanup stays event-driven. A
test or ArchUnit-style check could assert no FK crosses a prefix (optional).

### 4. Plain typed columns, no JSON
- `Track`: columns for title, description, album, art_url, format, version (`@Version`, optimistic locking),
  `text[]` tags with a GIN index; `Artist` and `TrackOwner` flattened with Spring Data `@Embedded` and column prefixes.
- `Account`: unique `(issuer, subject)`; timestamps as `timestamptz`.
- Waveform: status text with a check constraint, `peaks double precision[]`, `computed_at timestamptz`.
- Ids stay application-generated strings in `text` columns, to avoid uuid converters.

### 5. Account upsert
`INSERT ... ON CONFLICT (issuer, subject) DO UPDATE ... RETURNING *` via `DatabaseClient`. `findOrRegister` leaves an
existing row unchanged (no-op update so `RETURNING` yields it); `registerLogin` refreshes name, email and
`last_login_at`. Atomic, so the duplicate-key retry disappears.

### 6. Audio in chunked tables
`media_files(file_key, length, chunk_size, content_type)` and `media_chunks(file_key, n, data bytea)`, 255 KiB
chunks (GridFS's size, so range boundaries are unchanged). `store` re-chunks the incoming `DataBuffer` stream and inserts chunks inside one reactive transaction, so a
failed upload rolls back and the held-back-error / orphan-chunk workaround in `GridFsMediaStore` is removed.
`Media.content(first, last)` queries only the chunks `n` covering the range and trims the first and last, as today.
*Alternatives:* a single `bytea` per file (reads whole value), Large Objects (awkward with R2DBC), external storage
(separate change).

### 7. Search with full-text search
`search_tracks` carries a generated `tsvector` (English config, title weighted above description) with a GIN index.
`websearch_to_tsquery` ANDs words, but the spec requires *any* word to match, so the query words are split and joined
with `or` before `websearch_to_tsquery`; results are ordered by `ts_rank`, which keeps tracks matching more words
first. Blank query returns all tracks. Whole-word and variant matching come from the English stemmer; partial words do
not match. Implemented with `DatabaseClient` in `TrackSearch`.

### 8. Schema management
Root changelog `db/changelog/db.changelog-master.yaml` includes `db/changelog/<module>.yaml` per module
(plus one for the registry table, copied from the Modulith version's PostgreSQL schema). Spring Boot runs Liquibase on
the JDBC datasource at startup; Modulith's own schema initialisation is disabled.

### 9. Health and observability
Spring Boot reports an `r2dbc` health component (and a `db` one for the JDBC pool); the About page reads `r2dbc`
instead of `mongo` (a small UI change the proposal did not foresee). The Grafana HTTP dashboard's Mongo panels become
R2DBC and Hikari pool panels.

### 10. Tests and local run
`PostgreSQLContainer` with `@ServiceConnection` (serves both JDBC and R2DBC) replaces `MongoContainerDefinition`.
`docker-compose.yaml` swaps `mongo` and `mongo-express` for a `postgres` service plus Adminer on port 8081.

## Risks / Trade-offs

- [Search ranking and stemming differ from Mongo] → run all `search` spec scenarios; tune weights and config.
- [R2DBC embedded/array mapping is less magic than Mongo documents] → keep entities simple; add focused repository tests.
- [Registry write not atomic with data write] → unchanged from today; note in README.
- [Chunk upload in one transaction holds a connection for the upload's duration] → acceptable for a playground; chunks
  are small and uploads bounded.
- [Two pools (R2DBC and JDBC)] → size the JDBC pool small; it only serves the registry and Liquibase.

## Migration Plan

Fresh database, no data carried over. Deploy by starting Postgres and the new version; Liquibase creates the schema.
Rollback is reverting the PR; the old Mongo data is untouched by this change.
