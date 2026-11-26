# Design

## Context

See proposal.md for motivation. What the code does today, and constrains this change:

- Module events are Spring events kept in a durable outbox (Spring Modulith Event Publication Registry, PostgreSQL,
  blocking JDBC). `@ApplicationModuleListener` methods run on a task executor thread, after the publisher's
  transaction commits, and block (`.block()`) on reactive calls. Reactive services publish through
  `infrastructure.TransactionalEvents`, from a reactive transaction. A listener only fires for an event published
  inside a transaction: publishing outside one is silently dropped by `@TransactionalEventListener` semantics.
- Delivery is in the publishing JVM. Each instance handles its own events.
- The tracks list is read from the search read model (`GET /api/tracks/search?q=`), which lags the write.
  `SearchProjection` writes the copy, so only `search` knows when a track is really listable.
- `media` is track-agnostic: it stores audio under a caller-chosen key (the track id) and computes waveforms by
  key. `WaveformComputer.compute` is reached from the `AudioStored` listener and from `WaveformBackfill`
  (startup, outside any JDBC transaction).
- The UI is a static SvelteKit SPA served by the backend, same origin, session-cookie login (CSRF only on non-GET).
  `TrackList.svelte` polls pending waveforms every 3 s, 10 times. Playwright e2e tests run against the frontend
  preview server with the API mocked via `page.route`; there is no backend in them.
- Security: `SecurityConfig` permits everything not explicitly protected, so `GET /api/live` is anonymous-readable
  without a change.

## Goals / Non-Goals

**Goals:**
- Push "something you display changed" hints to every browser, across instances, with the audience enforced on the
  server.
- Keep module-to-module events exactly as they are (in-process, durable registry).
- Make adding a new live event (favourites, playlists) a small, local change: one event in the owning module, one
  mapping in `live`.

**Non-Goals:**
- Reliable or ordered delivery, replay, or exactly-once. Hints are best effort; the client refetches.
- Using NATS for anything but live hints in this change.
- Putting entity data in events.

## Decisions

### 1. NATS core as the backplane between instances, not PostgreSQL NOTIFY
Every instance subscribes once to `tunelad.>`; every hint, including those raised on the same instance, goes
through NATS. Core NATS is fire-and-forget fan-out, which is exactly the delivery that hints need.

- *PostgreSQL LISTEN/NOTIFY* needs no new service and `r2dbc-postgresql` exposes notifications as a `Flux`, but it
  pins a connection, takes a commit-time lock under write load, and has no subject hierarchy for per-account routing.
- *A broker with durable delivery (RabbitMQ, Kafka, JetStream)* would be overbuilt for hints, and Modulith
  externalization is outbound only, so it would not carry the in-JVM module events anyway.
- NATS is also the better foundation for later work (queue groups, JetStream) than NOTIFY, and the owner wants to
  learn it.
- *Always through NATS vs a local short-circuit*: emitting locally and also via NATS would keep a single instance
  working when NATS is down, but needs an origin id to avoid delivering twice. One code path is kept instead; the
  price is that a NATS outage stops live updates everywhere (accepted, see Risks).

### 2. SSE to browsers, on `GET /api/live`
One-way, native in WebFlux (`Flux<ServerSentEvent<String>>`), automatic reconnect in `EventSource`, and it sends
the session cookie, which matches the browser login. `EventSource` cannot send an `Authorization` header, so the
stream is scoped by the session; a bearer-token client simply gets the library events if it sends no cookie.
WebSocket was rejected: it adds a second protocol and a bidirectional channel nothing needs.

The SSE `event:` name is the event type; `data:` is the JSON body published on NATS, forwarded verbatim. A comment
heartbeat is merged into the stream every 15 s (`Flux.interval`), which satisfies the 30 s requirement with margin.
The OpenAPI description documents the endpoint as `text/event-stream` with the event types.

### 3. Events are ids only: the client invalidates and refetches
The body is `{"type": "<type>", "id": "<id>"}`. No entity content travels, so routing mistakes can leak at most an
id, and the browser's refetch goes through the existing authorization. A hint that arrives twice or late is harmless.

### 4. Subjects: `tunelad.library.<type>` and `tunelad.account.<accountKey>.<type>`
The first subject token after `tunelad` is the audience. `<type>` is the dotted event type (`track.indexed`,
`track.unindexed`, `waveform.computed`), which are subject tokens too. Ids stay in the body: they are arbitrary
strings and a `.`, `*`, `>` or space in an id would break a subject. The account id is likewise an arbitrary
string (issuer and subject based), so `<accountKey>` is its unpadded base64url encoding, which contains no `.`.

### 5. Routing is in process, one subscription per instance
```
NATS --one subscription "tunelad.>"--> LiveRouter --(audience, body)--> Sinks.many().multicast().directBestEffort()
                                                                              |
                                  GET /api/live  <--filter(audience admits me)--+   (one filter per connection)
```
`LiveRouter` takes the audience from the subject (never parses the body) and emits `(audience, body)` to a single
`directBestEffort` multicast sink. Each SSE connection subscribes and filters: a library event is admitted for
everyone, an account event only when the connection's account key equals the event's. `directBestEffort` drops events
for a slow subscriber without holding up the others, which is the "slow connection" requirement. Emission is
serialized because the NATS dispatcher delivers its messages one at a time.

The connection's account comes from `CurrentAccount.get()` when the request arrives (empty for anonymous). No
request parameter takes part in the decision. Authorization lives in our code rather than in NATS ACLs, so shared
playlists later mean one more rule here and no broker configuration.

*Alternative*: a map `accountKey -> sinks` avoids filtering every event per connection. Not needed at this scale; it
is a mechanical change if connection counts grow.

### 6. The `live` module listens to the owners of state, not to origin events
```
NewTrackSaved --> SearchProjection (writes copy) --> TrackIndexed ----+
TrackDeleted  --> SearchProjection (removes copy) -> TrackUnindexed --+--> live --> NATS
AudioStored   --> WaveformComputer (saves result) -> WaveformComputed -+
```
Reacting to `NewTrackSaved` directly would race the projection: the browser could refetch the list before the copy
exists. So `search` publishes `TrackIndexed` / `TrackUnindexed` after its write, and `media` publishes
`WaveformComputed` after saving a `READY` or `FAILED` waveform. Each module exposes its events in a new named
interface (`search::event`, `media::event`), as `track::event` does, and `live` allows exactly those two plus
`account` and `infrastructure`. The dependency points from `live` to the domain modules, so they know nothing
about the UI. If the mapping table grows large, the inverse (modules publish a generic `LiveUpdate`) is a mechanical
refactor.

`WaveformComputed` carries the media `key`, which `live` maps to the track id (the key is the track id, a convention
`track` already sets). `media` stays track-agnostic.

### 7. Where the new events are published
- **`search`**: `SearchProjection` listeners already run in a JDBC transaction on a task executor thread; after the
  blocking write they publish with the plain `ApplicationEventPublisher`. Listeners are at-least-once, so a hint may
  repeat; that is harmless.
- **`media`**: the result is saved from the `AudioStored` listener and from `WaveformBackfill`, and the latter has no
  surrounding JDBC transaction, where a plain publish would be dropped. So the save and the publish run together in
  a reactive transaction through `TransactionalEvents`, as `ChunkedMediaStore` does for `AudioStored`. `complete`
  reports whether it updated a row, so no event is raised for audio that is gone (failed upload, delete). Nothing is
  raised while the waveform stays `PENDING`.

### 8. Best effort, and never blocking the application
The bridge listener in `live` catches every exception from the NATS publish, logs it, and returns normally. A
failure therefore never leaves an incomplete registry publication that the registry would republish at restart
(republished hints are noise), and never fails the transaction of the publisher. The NATS connection is opened
asynchronously with unlimited reconnects, so the application starts without NATS and picks it up later (jnats
`connectAsynchronously` with reconnect-on-connect; to be confirmed against the client version). The reconnect buffer is
kept at zero or very small: stale hints are worth nothing, so a publish while disconnected is dropped, not queued.
NATS publishing is non-blocking in the client, and the listener runs on a task executor thread, never on an event
loop thread; the subscriber callback does only `tryEmit` on the sink.

### 9. Frontend
- `app/src/lib/live.svelte.ts`: one `EventSource('/api/live')` per tab, started from `+layout.svelte`, and reopened
  when the logged-in account changes (the layout already knows the session). Exposes `on(type, handler)` which
  returns an unsubscribe function, and a `reconnected` notification (an `open` after an `error`) that pages use to
  refresh.
- Tracks page: on `track.indexed` / `track.unindexed`, refetch with the search text currently entered, debounced
  (about 300 ms) so a burst becomes one request; on reconnect, refetch too. Replacing the list wholesale makes the
  "own change shown once" requirement hold without bookkeeping.
- `TrackList.svelte`: the polling (`POLL_INTERVAL`, `POLL_ATTEMPTS`) is removed. Rows still fetch their waveform when
  they near the viewport. On `waveform.computed` for a track already requested, refetch that waveform once; ids never
  requested are ignored, since the row fetches on first visibility. On reconnect, refetch the requested ones.
- Tests fake `EventSource` (vitest) and replace it with a controllable fake via `page.addInitScript` (Playwright),
  because the e2e suite has no backend. The service worker already leaves `/api/` navigations to the server, and
  `EventSource` requests are not navigations, so it does not touch the stream.

### 10. Configuration and test infrastructure
- `application.yml`: `tunelad.live.nats.url` (default `nats://localhost:4222`).
- `docker-compose.yaml`: a `nats` service (`nats:2`-based image, port 4222), no JetStream.
- Tests: a `NatsContainerDefinition` next to `PostgresContainerDefinition`, imported through
  `TestContainerDefinitions`, that registers the URL as a property. A plain `GenericContainer` is used, so nothing
  depends on a specific Testcontainers NATS module.

## Risks / Trade-offs

- [A NATS outage stops live updates for everyone] → Accepted for hints: the application keeps working, the client
  refetches on reconnect, and delivery resumes by itself. A local short-circuit is the documented escape hatch.
- [Anyone who can reach NATS can inject hints] → Hints carry ids only, so the worst case is useless refetches or
  a forged account-event nudge. Keep NATS on the internal network; authentication and TLS are out of scope here.
- [Stale session behind a long SSE connection] → The stream's audience is fixed when it opens. A logout in another
  tab keeps the first tab receiving that account's ids until it reconnects. Ids only, so the exposure is small; the
  UI reopens the stream on its own login/logout. Server-side expiry of the stream can follow if wanted.
- [Hint arrives before the data it points to] → Mitigated by emitting from `TrackIndexed` and `WaveformComputed`,
  which are raised after the write. The registry is at-least-once, so hints can repeat; refetching is idempotent.
- [Two-instance test is heavy] → It starts two Spring contexts on random ports against shared PostgreSQL and NATS
  containers. If port or MBean collisions make it flaky, fall back to two `LiveRouter`/bridge pairs on one NATS plus
  the real single-instance integration test.
- [HTTP/1.1 limits browsers to six connections per origin] → One stream per tab; many tabs can starve other
  requests on plain HTTP/1.1. HTTP/2 or a shared worker is a later optimisation.
- [Event bursts (bulk imports) cause many refetches] → The tracks page debounces its refetch.

## Migration Plan

Additive: no data migration and no change to existing endpoints. Deploy order: start NATS, then the application. The
application starts without NATS (live updates stay off until it is reachable), so the order is not enforced. Rollback
is removing the `live` module and the two event publications; nothing else depends on them.

## Open Questions

- Which exact jnats API and reconnect options give "start without NATS, no buffering while disconnected"? To be
  confirmed against the client version when implementing; it does not change the behaviour in the specs.
