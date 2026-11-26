# Tasks

## 1. Branch and build setup

- [x] 1.1 Create branch `refactor/migrate-to-postgresql` from an up-to-date `main`. Verify: `git branch --show-current`.
- [x] 1.2 Swap dependencies in `pom.xml` (remove MongoDB starters/Testcontainers Mongo; add R2DBC, r2dbc-postgresql, JDBC driver, Liquibase, `spring-modulith-starter-jdbc`, Testcontainers PostgreSQL) and replace `mongo`/`mongo-express` in `docker-compose.yaml` with Postgres. Verify: `docker compose up -d` brings Postgres up healthy. Commit `build(deps): add postgresql, r2dbc and liquibase`.

## 2. Datasource, transactions and Liquibase skeleton

- [x] 2.1 Configure R2DBC and JDBC datasources and Liquibase in `application.yml`; add root changelog and the `event_publication` changelog; replace `MongoTransactionConfig` with R2DBC and JDBC transaction managers; update `ReactiveTransactional` and `TransactionalEvents` docs. Verify: `ReactiveTransactionalEventsTest` ported to Postgres (new `PostgresContainerDefinition`) passes, and rolled-back transactions publish nothing. Commit `refactor(db): configure r2dbc, jdbc and liquibase`.

## 3. Account module

- [ ] 3.1 Add `account` changelog (`account_accounts`, unique `(issuer, subject)`), map `Account`, and rewrite `Accounts` upserts with `INSERT ... ON CONFLICT ... RETURNING`. Verify: `AccountRegisteringTest`, `KeycloakIntegrationTest`, `BrowserSessionSecurityTest` pass, including concurrent first logins yielding one account. Commit `refactor(account): store accounts in postgresql`.

## 4. Track module

- [ ] 4.1 Add `track` changelog (`track_tracks`, `text[]` tags with GIN index), map `Track` with `@Embedded` `Artist`/`TrackOwner` and `@Version`, replace `MongoTrackRepository`/`DbTracks`. Verify: `TrackApiTest`, `FailedUploadTest`, `DefaultTrackServiceUploadTest` pass, plus an optimistic-locking test. Commit `refactor(track): store tracks in postgresql`.

## 5. Media module

- [ ] 5.1 Add `media` changelog (`media_files`, `media_chunks`, `media_waveforms`); replace `GridFsMediaStore` with a chunked R2DBC store (transactional upload, range reads by chunk), map `Waveform`/`Waveforms`, update `WaveformBackfill`. Verify: `GridFsMediaStoreTest` (renamed), `MediaApiTest`, `WaveformTest` pass, including 200/206/416 range cases and a failed upload leaving no rows. Commit `refactor(media): store audio and waveforms in postgresql`.

## 6. Search module

- [ ] 6.1 Add `search` changelog (`search_tracks` with generated `tsvector` and GIN index); rewrite `SearchTracks`/`TrackSearch` with `DatabaseClient` (any-word match via `or`, `ts_rank` ordering, blank query returns all). Verify: search API tests cover every scenario in `openspec/specs/search/spec.md` and pass. Commit `refactor(search): use postgresql full-text search`.

## 7. Cleanup and docs

- [ ] 7.1 Remove leftover Mongo code, config and test containers; update `README.adoc` (database sections, run instructions), `CLAUDE.md`, the Grafana dashboard Mongo timings, and the Mongo wording in `openspec/config.yaml` rules. Verify: `grep -ril mongo` outside `openspec/changes/archive` finds nothing relevant. Commit `docs(db): replace mongodb with postgresql`.
- [ ] 7.2 Verify `ModularApplicationTests` still passes and that no foreign key crosses a module prefix (query `information_schema` in a test). Commit with the group it fixes, or `test(db): assert no cross-module foreign keys`.

## 8. Verification and PR

- [ ] 8.1 Run `./mvnw clean verify` and fix failures. Verify: build passes locally.
- [ ] 8.2 Push the branch and open a PR titled `refactor(db): migrate persistence from mongodb to postgresql`.
