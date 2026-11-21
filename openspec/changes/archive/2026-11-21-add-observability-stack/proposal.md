# Proposal

## Why

Observability is half there and switched off. The OpenTelemetry, Zipkin and Prometheus dependencies are commented
out in `pom.xml`, so the `traceId`/`spanId` log pattern prints empty ids. The compose stack (Zipkin, Prometheus 2.42,
Grafana 9.4) cannot work as written: Prometheus and Grafana reach the app and each other on `localhost`, which only
resolves with the host networking that was removed. The dashboards are JHipster-era JSON written against the old
Micrometer-to-Prometheus naming. Tune Lad is a playground for learning these tools, so OpenTelemetry is the thing to
set up properly: traces, metrics and logs, with a local backend that shows how they link up.

## What Changes

- Enable OpenTelemetry in the application through `spring-boot-starter-opentelemetry`: traces, metrics and logs are
  pushed over OTLP to a collector.
- Remove what OTLP replaces: the Zipkin starter, `micrometer-registry-prometheus`, and the `prometheus` actuator
  exposure.
- Replace the local compose observability entries with separate services on one compose network: an OpenTelemetry
  Collector, Tempo (traces), Loki (logs), Prometheus (metrics) and Grafana, with pinned image versions. Zipkin goes.
- Provision Grafana with datasources for Tempo, Loki and Prometheus, linked so a trace opens its logs and a log opens
  its trace.
- Replace the old JVM and WEB dashboards with new ones built for OpenTelemetry metric names: JVM and runtime, HTTP
  and API, and an overview with trace and log drill-downs. The old JSON is deleted.
- Carry the trace and span ids into logs, and keep OTLP export off in tests, so no test needs the stack running.
- Document the local stack in `README.adoc`.
- No REST API, UI or behaviour change, and nothing breaking.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This is developer and operations tooling: no behaviour of the REST API or the UI changes, so the change sets
`skip_specs: true`.

## Impact

- Modulith modules: none of `account`, `media`, `search` or `track` change. The `infrastructure` module's
  configuration is the only code-adjacent area. REST API: unchanged. UI: unchanged.
- Build: `pom.xml` dependencies (OpenTelemetry starter in; Zipkin starter and Prometheus registry out).
- Configuration: `application.yml` (OTLP endpoints, tracing and metrics settings, logging), a test profile that turns
  export off, and possibly a logback configuration if logs need an appender.
- Local environment: `docker-compose.yml` and `docker/` (collector, Tempo, Loki, Prometheus and Grafana configs and
  dashboards replacing `docker/prometheus/` and `docker/grafana/`).
- Docs: `README.adoc`.
- Suggested PR title: `feat(deps): add opentelemetry with a local grafana stack`

## Out of scope

- Module-level spans through `spring-modulith-observability`.
- Alerting rules, recording rules and long-term retention: the local stack is for development only.
- Production deployment of the stack, authentication in front of Grafana, and any hosted backend.
- Instrumenting the SvelteKit frontend (browser traces or metrics).
- Keeping or repairing the old JVM and WEB dashboards.
