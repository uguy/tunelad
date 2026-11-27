package org.tunelad.playlist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(title = "Playlist summary", description = "A playlist without its tracks")
public record PlaylistSummary(
		String id,
		String name,
		String description,
		@Schema(description = "How many tracks the playlist holds")
		int trackCount,
		@Schema(description = "When the playlist or its tracks last changed")
		Instant updatedAt
) {
}
