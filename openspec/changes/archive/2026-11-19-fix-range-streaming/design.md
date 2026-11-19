# Design

## Context

See proposal.md (Why). Current play path, all inside the `track` module:

```
TrackController.play(id, @RequestHeader("Range") String range)   // required, value unused
  -> TrackService.execute(PlayTrackCommand(id)) : Publisher<DataBuffer>
     -> Tracks.findById(id)  (404 via NotFoundException when empty)
     -> DbTracks.readMedia(id) = gridFs.getResources(id).flatMap(getContent)
  -> 200, chunked, no Content-Length / Accept-Ranges / Content-Range
```

Audio is stored with `ReactiveGridFsOperations.store(data, track.getId())`, so the GridFS file's `filename` is the track id. The bucket is the default `fs`, since no GridFS property is set. GridFS (a MongoDB spec, not a driver detail) stores one `fs.files` document per file (`_id`, `length`, `chunkSize`, default 261120 bytes) and the content as `fs.chunks` documents `{ files_id, n, data }` with `n = 0, 1, ...`.

The frontend is a static SPA (`ssr = false`, `prerender = true`). The `<audio>` player is mounted once in `+layout.svelte` and driven by a writable store holding a URL string (`#lib/store/media-source`). The tracks page sets it to `api/tracks/{id}/play`.

## Goals / Non-Goals

**Goals:**
- Serve a range by reading only the chunks that cover it, without blocking.
- Keep HTTP concerns (header parsing, status codes, response headers) in `track.web`, and storage concerns (GridFS layout) in `track.internal.db`.
- Let Spring do the RFC range arithmetic instead of hand-rolling it.
- Build start-at-position on native browser features (media fragments, native controls), not a custom player.

**Non-Goals:**
- A general-purpose range-serving abstraction for other modules or media.
- Caching the `fs.files` lookup between requests (every request does one `fs.files` query and one `fs.chunks` query, which is acceptable at this scale).

## Decisions

### 1. Service returns a media handle; the controller resolves the range

`TrackService.execute(PlayTrackCommand)` changes from `Publisher<DataBuffer>` to `Mono<TrackMedia>`, a new public type in the `track` package, next to `TrackDTO`:

```java
public interface TrackMedia {
    long length();                               // total size in bytes
    Flux<DataBuffer> content(long first, long last); // inclusive byte positions
}
```

The controller flow:

```
service.execute(new PlayTrackCommand(id))           // empty track -> NotFoundException -> 404
  .map(media -> {
     no/ignored Range -> 200, Content-Length = length, body content(0, length-1)
     one range        -> first = range.getRangeStart(length), last = range.getRangeEnd(length)
                         206, Content-Range bytes first-last/length,
                         Content-Length = last-first+1, body content(first, last)
     start >= length  -> 416, Content-Range bytes */length, empty body
  })
```

Range parsing and arithmetic use Spring's `HttpRange` (`parseRanges`, `getRangeStart(length)` and `getRangeEnd(length)`, which clamp the end and resolve suffix ranges). `getRangeStart` does not validate the start against the length, so the controller checks `first >= length` itself and answers 416. `parseRanges` throws on malformed input, and that becomes "ignore the header". A list with more than one range is also ignored. All responses set `Accept-Ranges: bytes` and `Content-Type: audio/mpeg`, and return `ResponseEntity<Flux<DataBuffer>>`. With an explicit `Content-Length`, WebFlux writes a fixed-length response, not a chunked one.

`PlayTrackCommand(String trackId)` is unchanged.

*Alternatives considered:*
- **The command carries a plain `ByteRange(Long first, Long last)`, and the service resolves it.** This keeps Spring types out of the command too, but re-implements suffix and clamp arithmetic that `HttpRange` already provides, and the service would then have to report "unsatisfiable" back to the web layer. Rejected: the handle approach keeps the same boundary (no Spring web types below `track.web`) with less code.
- **The command carries `HttpRange`.** That leaks a `spring-web` type into the module's command API. Rejected.
- **Return a Spring `Resource` and let `ResourceHttpMessageWriter` handle ranges.** `ReactiveGridFsResource` is not a `Resource`, and adapting it goes through `getInputStream()`, which blocks. Rejected (see "stay reactive").

### 2. Chunk-level seek in `DbTracks`

`Tracks.readMedia(String id)` becomes `Mono<TrackMedia> readMedia(String id)`, implemented in `DbTracks`:

1. `gridFsOperations.findOne(query(where("filename").is(id)))` returns a `GridFSFile` (`getObjectId()`, `getLength()`, `getChunkSize()`). If none is found, the result is empty, which the service maps to 404 like a missing track.
2. `content(first, last)`:
   - `firstChunk = first / chunkSize`, `lastChunk = last / chunkSize`, `skip = first % chunkSize`, `remaining = last - first + 1`;
   - `mongoOperations.find(query(where("files_id").is(objectId).and("n").gte(firstChunk).lte(lastChunk)).with(Sort.by("n")), Document.class, bucket + ".chunks")`;
   - each document's `data` (`org.bson.types.Binary`) is wrapped with `DefaultDataBufferFactory.sharedInstance.wrap(ByteBuffer.wrap(bytes, offset, len))`: the first chunk starts at `skip`, and every chunk is cut to what is left of `remaining`.

```
 first = 1_000_000, last = 1_499_999, chunkSize = 261_120
 chunk:   |  0  |  1  |  2  |  3  |  4  |  5  |  6  |
                             ^ skip 216_640  ^ cut
 query n in [3, 5]  ->  3 trimmed head, 4 whole, 5 trimmed tail  = 500_000 bytes
```

The chunks query is lazy (cold `Flux`) and runs only when WebFlux subscribes to the body. Wrapped heap buffers need no explicit release.

The service keeps the existence check on the track document (`tracks.findById`) before reading media, so the 404 behaviour stays the same.

*Alternatives considered:*
- **Stream from byte 0 and skip with `DataBufferUtils.skipUntilByteCount` / `takeUntilByteCount`.** It's simpler and stays on Spring Data's abstraction, but reads the whole prefix from Mongo on every seek. Rejected in favour of a real seek (user choice, B2).
- **The driver's `GridFSBucket.downloadToPublisher`.** The reactive driver has no offset support. Rejected.

### 3. Bucket name from configuration

`DbTracks` gets the bucket name from Spring Boot's GridFS bucket property, defaulting to `fs` (for example `@Value("${spring.data.mongodb.gridfs.bucket:fs}")`; check the exact key against Boot 4.1's Mongo properties during implementation). This keeps the hand-written `fs.chunks` query in step with the bucket `ReactiveGridFsTemplate` writes to.

### 4. Reactive, no blocking calls

Every step is a reactive Mongo call (`findOne`, `find`), and the byte slicing wraps arrays already in memory. Nothing blocks, so no `boundedElastic` offloading is needed.

### 5. OpenAPI description

The `play` operation gains:
- an `@Operation` summary;
- an optional `Range` header `@Parameter(in = ParameterIn.HEADER, required = false)`, with an example `bytes=0-`;
- `@ApiResponses` for 200 (whole file), 206 (partial content, `Content-Range` header), 404 and 416 (`Content-Range: bytes */<size>`), each with `audio/mpeg` content and an `Accept-Ranges` response header (`@Header`).

The description changes from "200 only, Range required" to the four responses above. No other operation changes.

### 6. Frontend: a structured player store and media fragments

- `#lib/store/media-source` holds `{ trackId: string, start: number } | null` instead of a URL string (initially `null`).
- `Player.svelte` derives `src = api/tracks/{trackId}/play` and appends `#t={start}` when `start > 0`. Browsers honour media fragments: they request the matching byte range (which now works) and start at that time. `currentTime` is bound to the `<audio>` element.
- Next to the audio element, `Player.svelte` shows a "Copy link at current time" button, rendered only when `trackId` is set. It writes `new URL(resolve('/tracks') + '?play=' + id + '&t=' + Math.floor(currentTime), location.origin)` with `navigator.clipboard.writeText`, and briefly shows "Link copied".
- `tracks/+page.svelte` reads `play` and `t` from `page.url.searchParams` (`$app/state`) when it mounts, in browser code only, never in `load`, because the page is prerendered. If `play` is present, it sets the store. `t` is parsed with `Number`, and a NaN or negative value becomes 0. The play buttons set `{ trackId, start: 0 }`.
- The deep link does not check that the id exists. An unknown id leaves the player with a 404 source, just as a stale track would.

*Alternative considered:* setting `audio.currentTime` on `loadedmetadata` instead of a fragment. That's more code and races with `preload="none"`, while fragments are native. The listener stays as a fallback only if a browser turns out to ignore fragments on this element.

### 7. Tests

- **REST (Testcontainers, `TrackApiTest`):** one test per scenario in the delta spec, named after the scenario. They use `Knees_Up.mp3` (3,315,251 bytes, about 13 chunks), so `bytes=1000000-1499999` crosses two chunk boundaries and trims both ends. `AbstractApiTest` gets a `play(id, rangeHeaderOrNull)` overload. The existing `play(id)` keeps sending `bytes=0-`, so the baseline tests stay as they are.
- **UI (Playwright, new `app/e2e/player.test.js`):** the SPA is served by `vite preview`. `page.route` mocks `api/tracks/search*` (one track) and `api/tracks/*/play` (fulfilled with `src/test/resources/mp3/Knees_Up.mp3`). The tests:
  - deep link with `t=90`: the audio `src` ends with `/api/tracks/X/play#t=90`;
  - no or invalid `t`: no fragment;
  - copy link: the button is absent before any track is loaded; after a load, `currentTime` is set to 95.7 through `page.evaluate`, then a click (with the `clipboard-read`/`clipboard-write` permissions granted) puts `.../tracks?play=X&t=95` on the clipboard.

  These run with `npx playwright test e2e/player.test.js`. The stale `e2e/test.js` and adding e2e to the Maven build are out of scope.

## Risks / Trade-offs

- [The hand-written `fs.chunks` query depends on GridFS's on-disk layout] → The layout is part of the published GridFS spec. The bucket name comes from configuration (Decision 3), and the mid-file range test runs against real Mongo with byte-exact comparison.
- [`bytes=0-` now gets 206 instead of 200] → The body is unchanged, and browsers expect 206 to treat media as seekable. The baseline test asserts only 2xx, by design.
- [VBR mp3: the start time maps to an estimated byte offset] → Accepted and listed out of scope. CBR files land exactly.
- [Autoplay policy can block playback on a deep link] → The player is still loaded and positioned, and the user presses play. Accepted.
- [Clipboard API needs a secure context] → `localhost` and HTTPS qualify. If `writeText` rejects, the button shows the link as text so it can be copied by hand.
- [The Playwright suite is not in the CI gate] → The new tests run manually during apply and the commands are listed in tasks. Wiring e2e into the build stays a separate change.

## Migration Plan

No data migration: GridFS content and documents are unchanged. Clients that sent `Range` keep working. Rollback means reverting the commit.
