# Spec Delta

## REMOVED Requirements

### Requirement: Delete all tracks
**Reason**: Clearing the whole library is only used to reset state between automated tests. No UI or other client uses it, and exposing an unauthenticated "delete everything" endpoint in the public REST API is a liability.
**Migration**: `DELETE /api/tracks` no longer exists and now responds 405 Method Not Allowed. Tests reset the database directly instead of going through the REST API. To clear a local library, wipe the local MongoDB data (`docker compose down -v`).
