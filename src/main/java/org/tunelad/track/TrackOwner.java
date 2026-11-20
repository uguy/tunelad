package org.tunelad.track;

import io.swagger.v3.oas.annotations.media.Schema;

/** The account that uploaded a track, with its display name at upload time. */
@Schema(description = "The account that uploaded the track, with its display name at upload time")
public record TrackOwner(String id, String displayName) {
}
