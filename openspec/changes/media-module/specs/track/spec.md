# Spec Delta

## REMOVED Requirements

### Requirement: Play a track
**Reason**: Moved to the new `media` capability, which owns stored audio and playback.
**Migration**: None. The requirement is unchanged in `specs/media`, and `GET /api/tracks/{id}/play` behaves exactly as before.

### Requirement: Serve a byte range of a track's audio
**Reason**: Moved to the new `media` capability, which owns stored audio and playback.
**Migration**: None. The requirement is unchanged in `specs/media`.

### Requirement: Reject an unsatisfiable byte range
**Reason**: Moved to the new `media` capability, which owns stored audio and playback.
**Migration**: None. The requirement is unchanged in `specs/media`.

### Requirement: Ignore unsupported Range headers
**Reason**: Moved to the new `media` capability, which owns stored audio and playback.
**Migration**: None. The requirement is unchanged in `specs/media`.

### Requirement: Start playback at a position from a link
**Reason**: Moved to the new `media` capability: it is player behaviour built on the play endpoint.
**Migration**: None. The requirement is unchanged in `specs/media`, and the UI behaves exactly as before.

### Requirement: Copy a link to the current playback position
**Reason**: Moved to the new `media` capability: it is player behaviour built on the play endpoint.
**Migration**: None. The requirement is unchanged in `specs/media`, and the UI behaves exactly as before.
