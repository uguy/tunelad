# Proposal

## Why

All persistence lives in MongoDB (documents, GridFS audio, text search, the Modulith event registry), which forces a
replica set just to get transactions. Moving everything to PostgreSQL gives real transactions, constraints and a
single relational store, and is a chance to learn Spring Data R2DBC and Liquibase alongside the existing stack.

## What Changes

- Replace MongoDB with PostgreSQL for **all** data: tracks, accounts, the search read model, waveforms, audio files
  and the Spring Modulith Event Publication Registry. MongoDB is removed from the code, build, compose file and tests.
- Access data with Spring Data R2DBC wherever it is supported. JDBC is used only where no reactive option exists:
  the Modulith event registry and Liquibase (startup only).
- Manage the schema with Liquibase, one changelog per Modulith module included from a root changelog.
- One PostgreSQL schema for the app, tables prefixed by owning module, plain typed columns (no JSON/JSONB), and no
  foreign key between modules: modules link by id/key only and clean up through events.
- Store audio in chunked tables (`media_files` + `media_chunks`), keeping byte-range reads for HTTP 206.
- Search uses PostgreSQL full-text search (English configuration, GIN index) with the same observable behaviour.
- Start from an empty database: **no data migration** from MongoDB.
- No REST API change, no breaking change, no UI redesign (the About page only reads the `r2dbc` health component instead of `mongo`).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. Requirements of `track`, `search`, `account` and `media` are unchanged; the existing scenarios (word variants,
case-insensitivity, relevance ordering, byte ranges, waveforms) must keep passing against PostgreSQL. This is a
pure refactor, so the change sets `skip_specs: true`.

## Impact

- **Modulith modules**: `track`, `search`, `account`, `media`, plus `infrastructure` (transactions, event
  registry). REST API: unchanged. UI: only the About page database status. Breaking REST API changes: none.
- **Code**: entities and repositories in each module, `GridFsMediaStore`, `TrackSearch`, `Accounts`,
  `MongoTransactionConfig`, `ReactiveTransactional`, `TransactionalEvents`, `application.yml`.
- **Dependencies** (`pom.xml`): drop the MongoDB starters and `testcontainers-mongodb`; add
  `spring-boot-starter-data-r2dbc`, `r2dbc-postgresql`, `postgresql` (JDBC), `spring-boot-starter-liquibase`,
  `spring-modulith-starter-jdbc`, `testcontainers-postgresql` and its R2DBC support.
- **Ops/docs**: `docker-compose.yaml` (Postgres replaces Mongo and Mongo Express), `README.adoc` database sections,
  `CLAUDE.md` mentions, the Grafana HTTP dashboard's Mongo timings, `openspec/config.yaml` task rules mentioning Mongo.
- **Test infrastructure**: Testcontainers PostgreSQL replaces `MongoContainerDefinition` in about a dozen test classes.

## Out of scope

- Migrating existing MongoDB data.
- JSON/JSONB columns (a possible later learning experiment).
- Moving audio to object storage or the filesystem.
- Any REST API or OpenAPI change, and any UI change beyond the About page health reading.
- Sharing the Keycloak database with the app's PostgreSQL instance.

## PR title

`refactor(db): migrate persistence from mongodb to postgresql`
