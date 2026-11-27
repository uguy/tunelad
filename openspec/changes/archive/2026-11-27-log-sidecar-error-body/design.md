# Design

## Context

`CadenceSource.request` ends with `.retrieve().bodyToFlux(...)`, so a 4xx/5xx becomes a `WebClientResponseException` whose `toString()` is only the status line. `IdentificationOnUpload` and `IdentificationController.run` both log `error.toString()`; the exception already carries the body (`getResponseBodyAsString()`), it is just never printed. The sidecar answers errors as `{"error": "<message>"}`.

## Goals / Non-Goals

**Goals:**
- One fix point so every caller of the source gets the message.

**Non-Goals:**
- Typed error handling for callers, or distinguishing client from server errors.

## Decisions

- **Map the error in `CadenceSource.request` with `onErrorMap(WebClientResponseException.class, ...)`**, to an `IllegalStateException` (message: `analysis service answered <status>: <body>`, cause: the original). Both log sites already log `error.toString()`, so neither changes. Alternatives: build the message at each log site (duplicated, and the next caller forgets it); a dedicated exception class (nothing catches it differently, so YAGNI).
- **Use the raw body, not a parsed `error` field.** The sidecar's format is ours but parsing adds a failure mode inside the error path; the JSON is short and readable as is. The body is cut to 300 characters with an ellipsis, and a blank body yields `analysis service answered <status>`.
- **Keep the cause.** Passing the original exception keeps the stack and the request URL available to anyone raising the log level.
- Reactive: pure operator on the existing stream, no blocking call. The body is already read by WebClient when the exception is built.

## Risks / Trade-offs

- [The body may carry something sensitive or noisy] → the sidecar only sends its own validation messages; length is capped.
- [`IllegalStateException` is generic] → acceptable as both callers only log it; revisit if a caller must react to the kind of failure.
