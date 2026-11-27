## MODIFIED Requirements

### Requirement: The analysis service can fail
If the analysis service is unreachable, times out or answers with an error, Identification SHALL end without failing the Track, leave the Performances unchanged and allow a re-run. When the service answers with an error status, the failure reported in the application's log SHALL include that status and the error message the service gave, shortened if very long; when the service gave none, the status alone.

#### Scenario: Service down
- **GIVEN** the analysis service is unreachable
- **WHEN** Identification runs
- **THEN** the Track and its Performances are unchanged, still `pending` (not `nothing_found`), and a re-run is possible

#### Scenario: Service rejects the request
- **GIVEN** the analysis service answers 400 with the error message "The request body is the audio."
- **WHEN** Identification runs
- **THEN** the Track and its Performances are unchanged and a re-run is possible
- **AND** the reported failure mentions the status 400 and "The request body is the audio."

#### Scenario: Error without a message
- **GIVEN** the analysis service answers 503 with an empty body
- **WHEN** Identification runs
- **THEN** the reported failure mentions the status 503 and nothing else is required of it

#### Scenario: A very long error message
- **GIVEN** the analysis service answers 500 with a message of several thousand characters
- **WHEN** Identification runs
- **THEN** the reported failure includes only the beginning of that message
