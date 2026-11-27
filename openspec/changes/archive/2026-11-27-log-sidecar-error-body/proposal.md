# Proposal

## Why

When the identification sidecar rejects a request, the only trace is `400 Bad Request from POST http://localhost:8090/identify`: the sidecar's own explanation (its JSON `error` field) is dropped. A stale sidecar container that still required a `tunes` parameter cost a long diagnosis for this reason, although the sidecar had said exactly what was wrong.

## What Changes

- When the analysis service answers with an error status, the failure Identification reports carries the status and the service's error message, not just the status line.
- The message is bounded in length, and a blank or missing body falls back to the status alone.
- Both places that log an Identification failure (after upload, and on a re-run) show it, with no change to when or whether Identification fails.
- No change to the REST API, the UI, or the sidecar.

## Capabilities

### New Capabilities

### Modified Capabilities
- `identification`: "The analysis service can fail" also requires that an error answer's message appears in the reported failure.

## Impact

- Modulith module: `identification` only (`CadenceSource`; the two existing log sites pick the message up unchanged). No REST API or UI change, nothing breaking.
- Tests: `IdentificationApiTest` and its stub sidecar.
- Suggested PR title: `fix(identification): surface the sidecar's error body in failures`

## Out of scope

- Detecting a sidecar older than the app (version field on `/health`, startup check).
- Showing the failure to the user in the UI or in the re-run response (the re-run stays 202 Accepted, asynchronous).
- Changing the sidecar's error format or status codes.
- Retrying failed analyses.
