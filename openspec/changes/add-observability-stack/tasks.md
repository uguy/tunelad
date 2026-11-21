# Tasks

## 1. Branch

- [x] 1.1 Create `feat/otel-observability` from an up-to-date `main` (the change's artifacts are uncommitted on `main` and come along); verify with `git branch --show-current`

## 2. Application exports OTLP (`feat(deps)`)

- [x] 2.1 Read the Spring Boot 4.1 reference for OTLP tracing, metrics and logs export and write down the exact property names and whether logs need a logback appender; verify by listing them in the commit message body
- [x] 2.2 In `pom.xml`, enable `spring-boot-starter-opentelemetry` and remove the Zipkin starter and `micrometer-registry-prometheus`; remove `prometheus` from the actuator exposure and its endpoint access in `application.yml`; verify `./mvnw -q compile` passes
- [x] 2.3 Configure OTLP export of the three signals to the collector's OTLP/HTTP port on `localhost`, keep sampling and the `traceId`/`spanId` log pattern, and set the service name; verify the application starts with no unknown-property or bean errors
- [x] 2.4 Add `src/test/resources/application.properties` turning each signal's export off; verify the full Java suite passes with `-Dspring.modulith.test.skip-optimizations=true` and its logs show no exporter connection errors
- [x] 2.5 Commit: `feat(deps): export traces, metrics and logs over otlp`

## 3. Local stack: collector and backends (`feat(deps)`)

- [x] 3.1 Replace the `zipkin`, `prometheus` and `grafana` entries of `docker-compose.yml` with `otel-collector`, `tempo`, `loki`, `prometheus` and `grafana` on one compose network, images pinned to current stable versions that exist (check each tag), publishing only the OTLP ports and Grafana (and Prometheus for debugging); verify `docker compose config` is valid and `docker compose up -d` leaves every service running
- [x] 3.2 Add the collector config under `docker/`: an OTLP receiver and pipelines to Tempo (traces), Loki (logs) and Prometheus (metrics), plus a `debug` exporter at first; verify that, with the application running and `GET /api/tracks` called, spans and metrics appear in the collector's output (log records arrive with 3.3)
- [x] 3.3 Logs need the OpenTelemetry logback appender (confirmed in 2.1: Boot 4.1.2 builds the log exporter but installs no appender, and its BOM does not manage one): add `opentelemetry-logback-appender-1.0` with a pinned version, a `logback-spring.xml` and the install call so records reach the SDK, and set `spring.reactor.context-propagation: auto` so logs written during a WebFlux request carry its trace id (without it the ids are empty); verify a request's log records reach the collector with that request's trace id
- [x] 3.4 Configure Tempo, Loki and Prometheus (including Prometheus's OTLP receiver flag) and drop the `debug` exporter; verify with their HTTP APIs that the trace, the logs and the metrics of the same request are queryable
- [x] 3.5 Delete the old `docker/prometheus/` config and the Zipkin entry's leftovers; verify `git grep -i zipkin` finds nothing outside the archive
- [x] 3.6 Commit: `feat(deps): add a local collector, tempo, loki and prometheus stack`

## 4. Grafana datasources and correlation (`feat(deps)`)

- [x] 4.1 Provision the Prometheus, Tempo and Loki datasources with explicit uids, linking a trace to its logs and a log's trace id to Tempo (the trace-to-metrics link waits for 5.2, which is when the metric names are known); verify each datasource's health check through Grafana's API
- [x] 4.2 Verify the correlation with the running application: a request's trace is found in Tempo, its logs in Loki through the link's query, and the log's trace id resolves back to that trace; record the queries used in the commit message body
- [x] 4.3 Commit: `feat(deps): provision grafana datasources with trace log and metric links`

## 5. New dashboards (`feat(deps)`)

- [ ] 5.1 Generate traffic against the running application and note the OpenTelemetry metric and label names that Prometheus actually holds for JVM, HTTP and runtime; verify with the Prometheus API
- [ ] 5.2 Build and provision the JVM and runtime, HTTP and API, and overview (trace and log drill-downs) dashboards from those names, replacing `docker/grafana/` and its old JSON, and add Tempo's trace-to-metrics link using those names; verify every panel's query returns series through Grafana, and that the old dashboards no longer appear
- [ ] 5.3 Commit: `feat(deps): replace the grafana dashboards with opentelemetry ones`

## 6. Documentation (`docs`)

- [ ] 6.1 Document the local stack in `README.adoc`: what `docker compose up -d` starts, the ports, how to open Grafana, trace and log lookup, and that tests never export; verify each documented command and URL works as written
- [ ] 6.2 Commit: `docs(deps): describe the local observability stack`

## 7. Wrap-up

- [ ] 7.1 Run `./mvnw clean verify` with Modulith's test skipping off (`-Dspring.modulith.test.skip-optimizations=true`); verify it passes
- [ ] 7.2 Open a PR titled `feat(deps): add opentelemetry with a local grafana stack`, only after 7.1 passes

## Workflow follow-up

- Archive the change on the feature branch before merge, with `/openspec-archive-change`.
