# Design

## Context

`pom.xml` has the OpenTelemetry starter, the Zipkin starter and `micrometer-registry-prometheus` commented out;
`application.yml` still carries tracing (sampling 1.0), the `prometheus` actuator exposure, an `application` metrics
tag and a log pattern that prints `traceId` and `spanId`. Compose runs Zipkin, Prometheus 2.42 and Grafana 9.4, wired
through `localhost`, which cannot work between containers. Tests run the full application against Testcontainers and
read the main `application.yml`; there is no test configuration file. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- One pipeline for the three signals: the application speaks OTLP only, and everything behind the collector can change
  without touching the application.
- A local stack that starts with `docker compose up -d` and shows traces, logs and metrics linked in Grafana.
- No test, and no request path, depends on the stack being up.

**Non-Goals:**
- Anything a production deployment would need (retention, auth, scaling, alerting).

## Decisions

**Application exports OTLP and nothing else.** `spring-boot-starter-opentelemetry` replaces the Zipkin starter and the
Prometheus registry; the `prometheus` entry leaves `management.endpoints.web.exposure.include`. Traces, metrics and
logs go to the collector's OTLP/HTTP port. The exact Boot 4.1 property names per signal are confirmed against its
reference in the first task, not guessed here.
*Alternative:* keep Prometheus scraping the actuator. Rejected: it keeps a host-to-container network path, which is
the thing breaking today's stack, and gives metrics a different pipeline from the other two signals.

**Logs.** Confirmed from the Boot 4.1.2 jars: it auto-configures the OTLP log exporter (`management.opentelemetry.logging.*`)
but installs no logback appender, and its BOM manages no appender version. So the OpenTelemetry logback appender is
added explicitly, with a pinned version and a `logback-spring.xml`. The existing `traceId`/`spanId` pattern stays, so
console logs remain correlated.

**Trace ids in logs need context propagation.** WebFlux hops threads, and the trace context does not follow by
itself: the `traceId`/`spanId` of the log pattern, and the ids on exported log records, stay empty even inside a
request. `spring.reactor.context-propagation: auto` (the `context-propagation` library is already on the classpath)
carries it across, and was confirmed to put the request's trace id on both the console line and the OTLP record.

**One compose network, the collector as the only published ingest.** Services: `otel-collector`, `tempo`, `loki`,
`prometheus`, `grafana`. The application runs on the host, so only the collector's OTLP ports (4317, 4318) and
Grafana (3000) must be reachable from it; Tempo, Loki and Prometheus talk over the compose network by service name,
which removes every `localhost` and `host.docker.internal` workaround. Prometheus's port may stay published for
debugging. All images are pinned to versions, with the Grafana and Prometheus ones raised from today's.

**Collector pipelines.** One OTLP receiver feeding three pipelines: traces to Tempo (OTLP), logs to Loki (its native
OTLP endpoint), metrics to Prometheus (its OTLP receiver, enabled by a startup flag, to confirm for the pinned
version). *Alternative:* Prometheus `remote_write` from the collector, a fallback if the OTLP receiver misbehaves.

**Grafana provisioned as code.** Datasources for Prometheus, Tempo and Loki are provisioned with explicit uids. Tempo
links a trace to its logs in Loki and to metrics, and Loki turns a trace id in a log record into a link to Tempo.
Dashboards are provisioned from JSON under `docker/`, replacing `docker/grafana/` and `docker/prometheus/`.

**New dashboards, written for OpenTelemetry names.** JVM and runtime; HTTP and API; an overview with trace and log
drill-downs. They are built and checked against the running pipeline, with no query written before its metric is
seen in Prometheus, because the translation of OTel metric and label names cannot be confirmed without running it.
*Alternative:* repair the old JHipster dashboards. Rejected by the user: they predate the naming and layout wanted.

**Metrics shaped for Prometheus.** Two settings found while building the dashboards, both in `application.yml`: timers
are exported in seconds (`base-time-unit`; the default of milliseconds gives `*_milliseconds` names and odd units), and
`http.server.requests` publishes percentile histogram buckets, without which the timer has only a `+Inf` bucket and
every `histogram_quantile` is `NaN`. Dashboards were checked for NaN-only results as well as empty ones, because a panel
can return rows and still show nothing.

**Tests never export.** A `src/test/resources/application.properties` turns each signal's export off. Spring Boot
loads it alongside the main `application.yml`, so it overrides only those keys instead of replacing the file.
Without it every test would try to reach a collector that is not there and log export failures.

**Reactive.** OTLP exporters batch and send on their own threads, off the WebFlux event loop, so no request path
blocks on telemetry; a missing collector costs warnings, not latency. No blocking call is introduced.

**Modules and API.** No Modulith module changes, so no `package-info.java` or event change. The REST API is
untouched, so the OpenAPI description does not change.

**On by default for local runs.** Export is enabled in `application.yml`, because compose is already required to run
the application (MongoDB, Keycloak). Running without the stack makes the exporters log warnings but does not affect
requests.
*Alternative:* an opt-in `otel` profile. Rejected for now: one more thing to remember, for a stack that is always
started with the others.

## Risks / Trade-offs

- [Boot 4.1 property names or the logs appender differ from what is assumed] -> the first task reads the reference and
  spikes logs before anything is built on them; the design above names the fallback (logback appender).
- [OTel to Prometheus name translation breaks dashboard queries] -> dashboards are built from metrics actually seen
  in Prometheus, never from names assumed up front.
- [The pinned images or flags (Prometheus OTLP receiver, Loki OTLP endpoint) are wrong for the chosen versions] ->
  verified by starting the stack and sending real telemetry from the application, as a task, before the README
  describes it.
- [Always-on export is noisy without the stack] -> accepted; opt-in profile is the fallback if it annoys.
- [More containers slow `docker compose up`] -> accepted for a development stack; resources are not tuned.

## Migration Plan

Developers recreate the compose stack: the old Zipkin, Prometheus and Grafana containers and their config are
replaced, and `docker compose up -d` brings up the new ones. Grafana's old dashboards are deleted with their
directory. Rollback is reverting the change. No data to migrate: the stack keeps nothing worth keeping.
