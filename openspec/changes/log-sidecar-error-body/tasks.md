# Tasks

## 1. Branch

- [x] 1.1 Create the feature branch `fix/identification-error-body` from an up-to-date `main`

## 2. Surface the error body

- [x] 2.1 In `CadenceSource.request`, map `WebClientResponseException` to an `IllegalStateException` carrying the status and the body (capped at 300 characters, status alone when blank), with the original as cause
- [x] 2.2 In `IdentificationApiTest`, let the stub sidecar answer a chosen status and body; add tests for a 400 with a message, a 503 with an empty body and a very long 500 body, asserting the reported failure text, that the Track is unchanged and that a re-run is possible
- [x] 2.3 Commit: `fix(identification): surface the sidecar's error body in failures`

## 3. Verify and ship

- [ ] 3.1 Run `./mvnw clean verify` until it passes
- [ ] 3.2 No README.adoc or GLOSSARY.md change is expected (not user-visible); confirm
- [ ] 3.3 Open a PR titled `fix(identification): surface the sidecar's error body in failures`
